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

package io.spine.testing.server.blackbox.probe;

import com.google.common.collect.ImmutableSet;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Error;
import io.spine.core.CommandValidationError;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.AbstractEventSubscriber;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.CommandErrored;
import io.spine.testing.server.blackbox.BlackBox;
import org.jspecify.annotations.Nullable;

import static io.spine.core.CommandValidationError.UNSUPPORTED_COMMAND_VALUE;
import static io.spine.server.commandbus.CommandException.ATTR_COMMAND_TYPE_NAME;
import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the commands posted to the {@link BlackBox} are not the
 * {@linkplain io.spine.server.bus.DeadMessageHandler "dead"} messages.
 *
 * <p>The guard subscribes to {@link CommandErrored} event.
 * It does not subscribe to external events.
 */
final class UnsupportedCommandGuard extends AbstractEventSubscriber {

    private static final String COMMAND_VALIDATION_ERROR_TYPE =
            CommandValidationError.getDescriptor()
                                  .getFullName();

    /** The name of the guarded Bounded Context. */
    private final String context;

    /**
     * A name of the command type for which the violation occurs in printable form.
     *
     * @see #checkAndRemember(CommandErrored)
     */
    private @Nullable String commandType;

    UnsupportedCommandGuard(String context) {
        super();
        this.context = context;
    }

    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return EventClass.setOf(CommandErrored.class);
    }

    /**
     * Checks if the given {@link CommandErrored} message represents an unsupported command
     * {@linkplain io.spine.server.commandbus.UnsupportedCommandException error}.
     */
    @Override
    public boolean canDispatch(EventEnvelope eventEnvelope) {
        var event = (CommandErrored) eventEnvelope.message();
        return checkAndRemember(event);
    }

    /**
     * Checks if the given {@link CommandErrored} event represents an "unsupported" error and,
     * if so, remembers its data.
     */
    @VisibleForTesting
    boolean checkAndRemember(CommandErrored event) {
        var error = event.getError();
        if (!isUnsupportedError(error)) {
            return false;
        }
        commandType = error.getAttributesMap()
                           .get(ATTR_COMMAND_TYPE_NAME)
                           .getStringValue();
        return true;
    }

    private static boolean isUnsupportedError(Error error) {
        return COMMAND_VALIDATION_ERROR_TYPE.equals(error.getType())
                && error.getCode() == UNSUPPORTED_COMMAND_VALUE;
    }

    /**
     * Throws an {@link AssertionError}.
     *
     * <p>Only reachable after unsupported command error
     * {@linkplain #canDispatch(EventEnvelope) is detected}.
     */
    @Override
    protected DispatchOutcome handle(EventEnvelope event) {
        failTest();

        // This return statement is unreachable,
        // since the previous statement throws an {@code Error}.
        return successfulOutcome(event);
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
