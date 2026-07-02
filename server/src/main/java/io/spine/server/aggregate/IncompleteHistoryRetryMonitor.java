/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.aggregate;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.spine.base.Error;
import io.spine.core.TenantId;
import io.spine.logging.WithLogging;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryMonitor;
import io.spine.server.delivery.FailedReception;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.tenant.TenantAwareRunner;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.function.LongSupplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * A {@link DeliveryMonitor} that retries the delivery of a signal whose reception failed with
 * an {@link IncompleteHistoryException}, once the storage has likely reached consistency.
 *
 * <p>An {@code IncompleteHistoryException} signals a <em>transient</em> failure to load an
 * aggregate: an eventually-consistent storage temporarily returned fewer events than the
 * aggregate's authoritative version implies. Retrying the same read after a short delay normally
 * succeeds. This monitor recognizes such a failure by its {@linkplain Error#getType() error type}
 * and, instead of {@linkplain FailedReception#markDelivered() draining} the message (the default
 * behavior) or {@linkplain FailedReception#repeatDispatching() re-dispatching it immediately}
 * (which does not let consistency settle and may spin), it
 * {@linkplain FailedReception#keepForRedelivery() keeps} the message and schedules a redelivery of
 * the shard after an exponentially growing back-off delay.
 *
 * <h2>Non-blocking</h2>
 *
 * <p>The delivery of a shard is single-threaded, so this monitor never sleeps on the delivery
 * thread. The back-off wait happens on a separate daemon {@link ScheduledExecutorService}, which
 * triggers {@link io.spine.server.delivery.Delivery#deliverMessagesFrom(ShardIndex)
 * Delivery.deliverMessagesFrom(index)} once the delay elapses. While a message is within its
 * back-off window, {@link #shouldDeliverNow(InboxMessage)} returns {@code false}, so the
 * framework's own immediate end-of-run re-trigger does not spin on a read that is expected to keep
 * failing until the storage settles.
 *
 * <h2>Giving up</h2>
 *
 * <p>After {@linkplain Builder#maxAttempts(int) a configurable number of attempts} the message is
 * {@linkplain FailedReception#markDelivered() drained} (marked delivered), so a permanently failing
 * signal does not stay in the inbox forever.
 *
 * <h2>Scope of the retry state</h2>
 *
 * <p>The per-message attempt counters and back-off timers are held in memory, on the node that
 * observed the failure. This is sufficient for recovering from eventual consistency: any node that
 * re-attempts the read after the data has settled succeeds; the back-off merely paces the retries
 * on a single node. The state does not survive a node restart and is not shared across nodes.
 *
 * <p>This monitor is <em>opt-in</em>: install it via
 * {@link io.spine.server.delivery.DeliveryBuilder#setMonitor(DeliveryMonitor)
 * DeliveryBuilder.setMonitor(...)}. Subclasses may override {@link #isRetryable(Error)} to react to
 * additional transient failures. The deferral is honored by the live delivery; a signal dispatched
 * during a projection {@linkplain io.spine.server.delivery.CatchUpProcess catch-up} is delivered
 * regardless (an aggregate-loading failure does not arise on that path).
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The monitor owns a daemon scheduler thread. The framework does <em>not</em> call
 * {@link #close()}, so a caller that replaces an installed monitor should {@code close()} the
 * previous instance. Even if it does not, the scheduler thread is a daemon and
 * {@linkplain java.util.concurrent.ThreadPoolExecutor#allowCoreThreadTimeOut(boolean) times out}
 * when idle, so an abandoned monitor neither leaks its thread nor blocks JVM shutdown.
 *
 * @see IncompleteHistoryException
 */
public class IncompleteHistoryRetryMonitor extends DeliveryMonitor
        implements WithLogging, AutoCloseable {

    /** The default maximum number of delivery attempts before the message is drained. */
    public static final int DEFAULT_MAX_ATTEMPTS = 6;

    /** The default delay before the first retry. */
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMillis(100);

    /** The default cap on the back-off delay. */
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(2);

    /** The default multiplier applied to the delay after each attempt. */
    public static final double DEFAULT_MULTIPLIER = 2.0;

    private final int maxAttempts;
    private final long initialDelayMillis;
    private final long maxDelayMillis;
    private final double multiplier;

    private final Cache<InboxMessageId, Attempt> attempts;
    private final ScheduledExecutorService scheduler;
    private final LongSupplier clock;

    /**
     * Creates a monitor with the {@linkplain Builder default settings}.
     */
    public IncompleteHistoryRetryMonitor() {
        this(newBuilder());
    }

    /**
     * Creates a monitor with the settings from the passed builder.
     */
    public IncompleteHistoryRetryMonitor(Builder builder) {
        this(builder, defaultScheduler(), System::currentTimeMillis);
    }

    /**
     * A test seam allowing to inject the scheduler and the clock.
     */
    IncompleteHistoryRetryMonitor(Builder builder,
                                  ScheduledExecutorService scheduler,
                                  LongSupplier clock) {
        super();
        this.maxAttempts = builder.maxAttempts;
        this.initialDelayMillis = builder.initialDelay.toMillis();
        this.maxDelayMillis = builder.maxDelay.toMillis();
        checkArgument(maxDelayMillis >= initialDelayMillis,
                      "`maxDelay` (%sms) must not be smaller than `initialDelay` (%sms).",
                      maxDelayMillis, initialDelayMillis);
        this.multiplier = builder.multiplier;
        this.scheduler = checkNotNull(scheduler);
        this.clock = checkNotNull(clock);
        // Keep an entry alive well past a full retry sequence; it is rewritten on every attempt,
        // and, on a success (which is not reported to the monitor), expires on its own.
        var expiryMillis = Math.max(60_000L, maxDelayMillis * (maxAttempts + 2L));
        this.attempts = CacheBuilder.newBuilder()
                                    .expireAfterWrite(Duration.ofMillis(expiryMillis))
                                    .build();
    }

    /**
     * If the reception failed with a {@linkplain #isRetryable(Error) retryable} error, keeps the
     * message for a delayed redelivery and schedules the redelivery of its shard.
     *
     * <p>Otherwise, falls back to the
     * {@linkplain DeliveryMonitor#onReceptionFailure(FailedReception) default} behavior.
     *
     * <p>Once the configured number of attempts is exhausted, the message is drained (marked
     * delivered) to avoid keeping a permanently failing signal in the inbox.
     */
    @Override
    public FailedReception.Action onReceptionFailure(FailedReception reception) {
        if (!isRetryable(reception.error())) {
            return super.onReceptionFailure(reception);
        }
        var message = reception.message();
        var decision = new Decision();
        attempts.asMap()
                .compute(message.getId(), (id, existing) -> next(existing, decision));
        if (decision.giveUp) {
            logger().atDebug().log(() ->
                    "Giving up the delivery of `" + message.getId().getUuid()
                            + "` after " + maxAttempts + " attempts to load an aggregate with "
                            + "incomplete history; marking the message as delivered.");
            return reception.markDelivered();
        }
        if (decision.scheduleDelayMillis >= 0
                && !scheduleRetry(message, decision.scheduleDelayMillis)) {
            // The scheduler is unavailable (e.g. the monitor was closed); drain the message
            // instead of leaving it in `TO_DELIVER` with no pending retry.
            return reception.markDelivered();
        }
        return reception.keepForRedelivery();
    }

    /**
     * Computes the next attempt state, recording the decision to make into the passed holder.
     *
     * @return the updated attempt state, or {@code null} to remove it (when giving up)
     */
    private Attempt next(Attempt existing, Decision decision) {
        var now = clock.getAsLong();
        if (existing != null && now < existing.nextAttemptMillis()) {
            // A due retry is already scheduled; this is an earlier re-attempt, e.g. caused by
            // another message arriving to the shard. Keep the message without rescheduling.
            return existing;
        }
        var thisFailure = existing == null ? 1 : existing.failures() + 1;
        if (thisFailure >= maxAttempts) {
            decision.giveUp = true;
            return null;
        }
        var delay = backoffMillis(thisFailure);
        decision.scheduleDelayMillis = delay;
        return new Attempt(thisFailure, now + delay);
    }

    /**
     * Tells the {@code Delivery} not to immediately re-run a shard whose only pending message is
     * one currently waiting out its retry back-off.
     */
    @Override
    public boolean shouldDeliverNow(InboxMessage message) {
        var attempt = attempts.getIfPresent(message.getId());
        return attempt == null || clock.getAsLong() >= attempt.nextAttemptMillis();
    }

    /**
     * Tells whether the reception should be retried in response to the passed error.
     *
     * <p>By default, matches an {@link IncompleteHistoryException}. Override to recognize other
     * transient failures.
     */
    protected boolean isRetryable(Error error) {
        return IncompleteHistoryException.class.getCanonicalName()
                                               .equals(error.getType());
    }

    /**
     * Schedules the redelivery of the message's shard after the given delay.
     *
     * @return {@code true} if the retry was scheduled, {@code false} if the scheduler rejected it
     *         (for example, because the monitor has been {@linkplain #close() closed})
     */
    private boolean scheduleRetry(InboxMessage message, long delayMillis) {
        var index = message.shardIndex();
        var tenant = message.tenant();
        try {
            @SuppressWarnings("FutureReturnValueIgnored") // Fire-and-forget; failures are logged.
            var unused =
                    scheduler.schedule(() -> redeliver(index, tenant), delayMillis, MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            logger().atDebug()
                    .withCause(e)
                    .log(() -> "The retry scheduler is unavailable; draining message `"
                            + message.getId().getUuid() + "`.");
            return false;
        }
    }

    @SuppressWarnings("OverlyBroadCatchBlock") // A scheduler task must not die on any failure.
    private void redeliver(ShardIndex index, TenantId tenant) {
        try {
            // Re-resolve the delivery from the environment (as the framework's own re-triggers do),
            // assuming it is the same delivery on which this monitor is installed.
            var delivery = ServerEnvironment.instance()
                                            .delivery();
            TenantAwareRunner.with(tenant)
                             .run(() -> delivery.deliverMessagesFrom(index));
        } catch (Exception e) {
            logger().atError()
                    .withCause(e)
                    .log(() -> "Failed to re-deliver the shard `" + index.getIndex()
                            + "` for an incomplete-history retry.");
        }
    }

    @SuppressWarnings("WeakerAccess") // Package-private for testing the back-off schedule.
    long backoffMillis(int failure) {
        var delay = initialDelayMillis * Math.pow(multiplier, failure - 1.0);
        var capped = Math.min(delay, (double) maxDelayMillis);
        return (long) capped;
    }

    /**
     * Shuts down the scheduler used for the delayed retries.
     *
     * <p>The framework does not call this method; a caller that discards the monitor may call it to
     * release the scheduler thread eagerly instead of waiting for its idle time-out.
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    /**
     * Creates a new builder for the monitor.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    private static ScheduledExecutorService defaultScheduler() {
        ThreadFactory factory = runnable -> {
            var thread = new Thread(runnable, "incomplete-history-retry");
            thread.setDaemon(true);
            return thread;
        };
        var executor = new ScheduledThreadPoolExecutor(1, factory);
        // Release the worker thread when idle, so a monitor discarded without being closed
        // (the framework does not call `close()`) does not keep a thread alive indefinitely.
        executor.setKeepAliveTime(1, MINUTES);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * The state of the retries for a single {@code InboxMessage}.
     *
     * @param failures
     *         the number of times the delivery of the message has failed so far
     * @param nextAttemptMillis
     *         the wall-clock time (in milliseconds) at which the next attempt is due
     */
    private record Attempt(int failures, long nextAttemptMillis) {
    }

    /**
     * A mutable holder for the outcome of an atomic state transition.
     */
    private static final class Decision {

        private boolean giveUp;
        private long scheduleDelayMillis = -1;
    }

    /**
     * A builder for {@link IncompleteHistoryRetryMonitor}.
     */
    public static final class Builder {

        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration initialDelay = DEFAULT_INITIAL_DELAY;
        private Duration maxDelay = DEFAULT_MAX_DELAY;
        private double multiplier = DEFAULT_MULTIPLIER;

        private Builder() {
        }

        /**
         * Sets the maximum number of delivery attempts before the message is drained.
         *
         * <p>Must be at least {@code 1}. A value of {@code 1} drains on the first failure
         * (i.e. no retry).
         */
        public Builder maxAttempts(int maxAttempts) {
            checkArgument(maxAttempts >= 1, "`maxAttempts` must be positive.");
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the delay before the first retry.
         */
        public Builder initialDelay(Duration initialDelay) {
            checkNotNull(initialDelay);
            checkArgument(!initialDelay.isNegative() && !initialDelay.isZero(),
                          "`initialDelay` must be positive.");
            this.initialDelay = initialDelay;
            return this;
        }

        /**
         * Sets the cap on the back-off delay.
         */
        public Builder maxDelay(Duration maxDelay) {
            checkNotNull(maxDelay);
            checkArgument(!maxDelay.isNegative() && !maxDelay.isZero(),
                          "`maxDelay` must be positive.");
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * Sets the multiplier applied to the delay after each attempt.
         *
         * <p>Must be at least {@code 1.0}.
         */
        public Builder multiplier(double multiplier) {
            checkArgument(multiplier >= 1.0, "`multiplier` must be at least 1.0.");
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Builds a new monitor with the configured settings.
         *
         * @throws IllegalArgumentException
         *         if {@code maxDelay} is smaller than {@code initialDelay} (also enforced by the
         *         {@linkplain IncompleteHistoryRetryMonitor#IncompleteHistoryRetryMonitor(Builder)
         *         constructor})
         */
        public IncompleteHistoryRetryMonitor build() {
            return new IncompleteHistoryRetryMonitor(this);
        }
    }
}
