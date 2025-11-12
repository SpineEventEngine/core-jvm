/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.testing.server.blackbox.probe;

import com.google.common.collect.ImmutableSet;
import io.spine.annotation.VisibleForTesting;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.AbstractEventSubscriber;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.HandlerFailedUnexpectedly;

import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;
import static io.spine.type.Json.toJson;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Performs logging of failed signals handlers or fails the test
 * depending upon the current context exception tolerance.
 */
public final class FailedHandlerGuard
        extends AbstractEventSubscriber
        implements DiagnosticLogging {

    private HandlerFailureTolerance tolerance = HandlerFailureTolerance.RAISE_AND_FAIL;

    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return EventClass.setOf(HandlerFailedUnexpectedly.class);
    }

    /**
     * Always dispatched {@link HandlerFailedUnexpectedly} event.
     */
    @Override
    public boolean canDispatch(EventEnvelope eventEnvelope) {
        return true;
    }

    @Override
    protected DispatchOutcome handle(EventEnvelope eventEnvelope) {
        var event = (HandlerFailedUnexpectedly) eventEnvelope.message();
        on(event);
        return successfulOutcome(eventEnvelope);
    }

    /**
     * Throws an {@link org.opentest4j.AssertionFailedError AssertionFailedError}
     * unless the guard is configured to {@linkplain #tolerateFailures() tolerate} exceptions.
     *
     * <p>If the guard is tolerating exceptions, logs the handler failure.
     */
    @VisibleForTesting
    @SuppressWarnings("CompileTimeConstant") /* Logging the msg with some complex formatting. */
    void on(HandlerFailedUnexpectedly event) {
        var msg = format(
                "The entity (state type `%s`) could not handle the signal `%s`:%n%s%n",
                event.getEntity().getTypeUrl(),
                event.getHandledSignal().getTypeUrl(),
                event.getError().getMessage()
        );
        switch (tolerance) {
            case LOG:
                log(msg, event);
                break;
            case RAISE_AND_FAIL:
            default:
                fail(() -> msg + lineSeparator() + toJson(event));
                break;
        }
    }

    /**
     * Asks the guard to tolerate exceptions.
     */
    public void tolerateFailures() {
        tolerance = HandlerFailureTolerance.LOG;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code BlackBoxBoundedContext} only consumes domestic events.
     */
    @Override
    public ImmutableSet<EventClass> domesticEventClasses() {
        return eventClasses();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code BlackBoxBoundedContext} does not consume external events.
     */
    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return EventClass.emptySet();
    }
}
