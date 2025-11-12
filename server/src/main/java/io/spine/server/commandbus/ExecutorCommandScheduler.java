/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.commandbus;

import io.spine.annotation.VisibleForTesting;
import io.spine.core.Command;
import io.spine.logging.WithLogging;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The command scheduler implementation which uses basic Java task scheduling features.
 *
 * <p><b>NOTE:</b> please use <a href="https://github.com/SpineEventEngine/gcloud-java">
 * another implementation</a> in applications running under the Google App Engine.
 *
 * @see ScheduledExecutorService
 */
public class ExecutorCommandScheduler extends CommandScheduler implements WithLogging {

    private static final int MIN_THREAD_POOL_SIZE = 5;
    private static final int NANOS_IN_MILLISECOND = 1_000_000;
    private static final int MILLIS_IN_SECOND = 1_000;

    private final ScheduledExecutorService executorService;

    @VisibleForTesting
    public ExecutorCommandScheduler(ScheduledExecutorService executorService) {
        super();
        this.executorService = executorService;
    }

    public ExecutorCommandScheduler() {
        this(Executors.newScheduledThreadPool(MIN_THREAD_POOL_SIZE));
    }

    @SuppressWarnings("FutureReturnValueIgnored") // Error handling is done manually.
    @Override
    protected void doSchedule(Command command) {
        final var delayMillis = delayMillisecondsOf(command);
        executorService.schedule(() -> safePost(command),
                                 delayMillis, MILLISECONDS);
    }

    /**
     * Posts a command catching all errors along the way.
     *
     * @apiNote
     * Such post operation is required for the {@link ScheduledExecutorService} as any uncaught
     * throwable in its action will lead to the worker thread silently halting. This method logs an
     * error and keeps the thread "alive".
     */
    private void safePost(Command command) {
        try {
            post(command);
        } catch (Throwable t) {
            logger().atError().withCause(t).log(() -> format(
                    "Error posting command `%s` with ID `%s`: `%s`.",
                    command.enclosedTypeUrl(),
                    command.getId()
                           .getUuid(),
                    t.getLocalizedMessage()));
        }
    }

    private static long delayMillisecondsOf(Command command) {
        var schedule = command.getContext().getSchedule();
        var delay = schedule.getDelay();
        var delaySec = delay.getSeconds();
        long delayMillisFraction = delay.getNanos() / NANOS_IN_MILLISECOND;

        /*
         * Maximum value of {@link Duration#getSeconds()} is
         * <a href="https://github.com/google/protobuf/blob/master/src/google/protobuf/duration.proto"+315,576,000,000.</a>.
         *
         * {@link Long.MAX_VALUE} is +9,223,372,036,854,775,807. That's why it is safe to multiply
         * {@code delaySec * MILLIS_IN_SECOND}.
         */
        var absoluteMillis = delaySec * MILLIS_IN_SECOND + delayMillisFraction;
        return absoluteMillis;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        executorService.shutdown();
    }
}
