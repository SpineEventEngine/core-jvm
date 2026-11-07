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
import io.spine.system.server.event.UnsupportedCommandReceived;
import io.spine.testing.server.blackbox.BlackBox;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the commands posted to the {@link BlackBox} are not the
 * {@linkplain io.spine.server.bus.DeadMessageHandler "dead"} messages.
 *
 * <p>The guard receives the {@link UnsupportedCommandReceived} event, and fails the test.
 * It does not subscribe to external events.
 *
 * <p>The guard overrides {@link #handle(EventEnvelope)} instead of declaring a subscriber
 * method: an {@link AssertionError} thrown by a subscriber method would be turned into
 * a dispatching error and would not reach the test.
 */
final class UnsupportedCommandGuard extends AbstractEventSubscriber {

    /** The name of the guarded Bounded Context. */
    private final String context;

    /**
     * A name of the command type that has no handler, in printable form.
     *
     * @see #on(UnsupportedCommandReceived)
     */
    private @Nullable String commandType;

    UnsupportedCommandGuard(String context) {
        super();
        this.context = context;
    }

    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return EventClass.setOf(UnsupportedCommandReceived.class);
    }

    /**
     * Always dispatches the {@link UnsupportedCommandReceived} event.
     */
    @Override
    public boolean canDispatch(EventEnvelope eventEnvelope) {
        return true;
    }

    /**
     * Throws an {@link AssertionError}.
     */
    @Override
    protected DispatchOutcome handle(EventEnvelope eventEnvelope) {
        var event = (UnsupportedCommandReceived) eventEnvelope.message();
        on(event);

        // This return statement is unreachable,
        // since the previous statement throws an {@code AssertionError}.
        return successfulOutcome(eventEnvelope);
    }

    /**
     * Remembers the type of the command, and throws an {@link AssertionError}.
     */
    @VisibleForTesting
    void on(UnsupportedCommandReceived event) {
        commandType = TypeUrl.parse(event.getTypeUrl())
                             .typeName()
                             .value();
        failTest();
    }

    /**
     * Throws an {@link AssertionError}.
     *
     * <p>The method is assumed to be called after a violation was found for some
     * {@link #commandType}.
     */
    private void failTest() {
        requireNonNull(commandType);
        var msg = format(
                "The command type `%s` does not have a handler in the context `%s`.",
                commandType, context
        );
        fail(msg);
    }

    @VisibleForTesting
    @Nullable String commandType() {
        return commandType;
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
