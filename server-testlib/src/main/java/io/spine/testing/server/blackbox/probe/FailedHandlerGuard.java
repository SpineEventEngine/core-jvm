/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
 * Performs logging of failed signal handlers or fails the test
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
     * Always dispatches the event of a failed handler.
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
     * <p>The {@code BlackBox} bounded context only consumes domestic events.
     */
    @Override
    public ImmutableSet<EventClass> domesticEventClasses() {
        return eventClasses();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code BlackBox} bounded context does not consume external events.
     */
    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return EventClass.emptySet();
    }
}
