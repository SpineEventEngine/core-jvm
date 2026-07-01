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

package io.spine.server.delivery.given;

import com.google.common.collect.ImmutableList;
import io.spine.environment.Tests;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DefaultRepository;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.DeliveryMonitor;
import io.spine.server.delivery.FailedReception;
import io.spine.server.delivery.InboxContents;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.LocalDispatchingObserver;
import io.spine.server.delivery.ShardObserver;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.test.delivery.Receptionist;
import io.spine.test.delivery.command.TurnConditionerOn;
import io.spine.testing.server.blackbox.BlackBox;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * Test environment for {@link io.spine.server.delivery.ReceptionFailureTest}.
 */
public final class ReceptionFailureTestEnv {

    private ReceptionFailureTestEnv() {
    }

    public static BlackBox blackBox() {
        var repository = DefaultRepository.of(ReceptionistAggregate.class);
        var context = BlackBox.singleTenantWith(repository);
        return context;
    }

    /**
     * Creates a single-tenant black box over the {@link ReceptionistAggregate} and the
     * {@link CalcAggregate}, with the passed {@link DiagnosticMonitor} registered as an event
     * dispatcher.
     *
     * <p>The {@code CalcAggregate} serves as a "bystander" of a different type: its applier is not
     * governed by {@link ReceptionistAggregate#makeApplierFail()}, so it can be delivered
     * successfully while a {@code ReceptionistAggregate} in the same shard keeps failing.
     *
     * <p>The monitor allows observing the diagnostic events, such as
     * {@link io.spine.system.server.CannotDispatchDuplicateCommand}, produced while the commands
     * are delivered to the aggregates.
     */
    public static BlackBox blackBoxWith(DiagnosticMonitor monitor) {
        var builder = BoundedContextBuilder.assumingTests();
        builder.add(DefaultRepository.of(ReceptionistAggregate.class));
        builder.add(DefaultRepository.of(CalcAggregate.class));
        var context = builder.build();
        context.internalAccess()
               .registerEventDispatcher(monitor);
        return BlackBox.from(context);
    }

    public static void configureDelivery(DeliveryMonitor monitor) {
        var delivery = Delivery.newBuilder()
                .setMonitor(monitor)
                .build();
        delivery.subscribe(new IgnoringObserver());
        ServerEnvironment.when(Tests.class)
                         .use(delivery);
    }

    /**
     * Configures a {@code Delivery} that dispatches the messages synchronously, in the calling
     * thread, using the passed monitor.
     *
     * <p>Unlike {@link #configureDelivery(DeliveryMonitor)}, which delivers asynchronously and
     * thus requires a {@link #sleep()}, this variant makes the delivery deterministic: by the time
     * {@code BlackBox.receivesCommand(...)} returns, the command has already been delivered to its
     * target.
     */
    public static void configureSynchronousDelivery(DeliveryMonitor monitor) {
        var delivery = Delivery.newBuilder()
                .setMonitor(monitor)
                .build();
        delivery.subscribe(new LocalDispatchingObserver());
        ServerEnvironment.when(Tests.class)
                         .use(delivery);
    }

    public static void sleep() {
        sleepUninterruptibly(Duration.ofMillis(900));
    }

    public static Receptionist receptionist(String receptionistId, int cmdsHandled) {
        return Receptionist.newBuilder()
                .setId(receptionistId)
                .setHowManyCmdsHandled(cmdsHandled)
                .build();
    }

    public static TurnConditionerOn tellToTurnConditioner(String receptionistId) {
        var command = TurnConditionerOn
                .newBuilder()
                .setReceptionistId(receptionistId)
                .build();
        return command;
    }

    /**
     * Fetches the contents of a single shard.
     *
     * <p>In case there are many shards configured, this method
     * throws an {@code IllegalStateException}.
     */
    public static ImmutableList<InboxMessage> inboxMessages() {
        var contents = InboxContents.get();
        checkState(contents.size() == 1);
        var messages = contents.values()
                               .iterator()
                               .next();
        return messages;
    }

    /**
     * A shard observer that deliberately ignores any exceptions thrown when dispatching
     * inbox messages.
     */
    private static final class IgnoringObserver implements ShardObserver {

        @Override
        public void onMessage(InboxMessage message) {
            new Thread(() -> runDelivery(message)).start();
        }

        @SuppressWarnings("resource")
        private static void runDelivery(InboxMessage message) {
            var tenant = message.tenant();
            var index = message.shardIndex();
            var delivery = ServerEnvironment.instance()
                                            .delivery();
            try {
                TenantAwareRunner.with(tenant)
                                 .run(() -> delivery.deliverMessagesFrom(index));
            } catch (Exception ignored) {
                // Do nothing.
            }
        }
    }

    /**
     * A delivery monitor that remembers the last observed reception failure.
     *
     * <p>Allows to specify a {@link FailureResolver} and act upon the observed failure.
     */
    public static final class ObservingMonitor extends DeliveryMonitor {

        private @Nullable FailedReception lastFailure = null;
        private @Nullable FailureResolver resolver = null;

        @Override
        public FailedReception.Action onReceptionFailure(FailedReception reception) {
            lastFailure = reception;
            if (resolver != null) {
                return resolver.onFailure(reception);
            }
            return super.onReceptionFailure(reception);
        }

        public Optional<FailedReception> lastFailure() {
            return Optional.ofNullable(lastFailure);
        }

        /**
         * Specifies the resolver for this monitor.
         */
        public void setResolver(FailureResolver resolver) {
            this.resolver = checkNotNull(resolver);
        }
    }

    /**
     * Allows to act upon an observed {@link FailedReception}.
     */
    @FunctionalInterface
    public interface FailureResolver {

        /**
         * Returns an action in response to a failed reception.
         */
        FailedReception.Action onFailure(FailedReception reception);
    }

    /**
     * A delivery monitor that marks the message causing a reception failure
     * as {@linkplain io.spine.server.delivery.InboxMessageStatus#DELIVERED delivered}.
     */
    public static final class MarkFailureDeliveredMonitor extends DeliveryMonitor {

        private boolean failureReceived = false;

        /**
         * In case the reception of the {@code InboxMessage} failed,
         * mark it as delivered anyway.
         */
        @Override
        public FailedReception.Action onReceptionFailure(FailedReception reception) {
            this.failureReceived = true;
            return reception.markDelivered();
        }

        public boolean failureReceived() {
            return failureReceived;
        }
    }
}
