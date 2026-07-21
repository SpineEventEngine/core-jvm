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

import io.spine.base.Error;
import io.spine.core.CommandValidationError;
import io.spine.core.Event;
import io.spine.core.EventValidationError;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.core.CommandValidationError.DUPLICATE_COMMAND_VALUE;
import static io.spine.core.EventValidationError.DUPLICATE_EVENT_VALUE;
import static java.lang.String.format;

/**
 * This guard ensures that the message was not yet dispatched to the {@link Aggregate aggregate}.
 *
 * <p>The check scans the aggregate's recent event history, which includes the events
 * committed by the earlier dispatches of the current delivery batch even before they
 * reach the durable journal — so a duplicate arriving within one batch is caught too.
 */
final class DoubleDispatchGuard {

    private final Aggregate<?, ?, ?> aggregate;

    /**
     * Whether this history-backed guard is active.
     *
     * <p>Off by default: deduplication is primarily the delivery layer's responsibility, and this
     * guard is an opt-in per-repository backstop (see
     * {@link AggregateRepository#useDoubleDispatchGuard()}).
     */
    private boolean enabled = false;

    /**
     * The number of the most recent events scanned for a duplicate.
     *
     * <p>Zero while the guard is disabled: both fields are assigned together
     * in {@link #enable(int)}, and the depth is never read before that.
     */
    private int historyDepth = 0;

    DoubleDispatchGuard(Aggregate<?, ?, ?> aggregate) {
        this.aggregate = aggregate;
    }

    /**
     * Enables the guard, scanning up to {@code historyDepth} most recent events for a
     * duplicate on each dispatch.
     */
    void enable(int historyDepth) {
        checkArgument(historyDepth > 0);
        this.enabled = true;
        this.historyDepth = historyDepth;
    }

    /**
     * Checks that the command was not dispatched to the aggregate.
     *
     * @param command
     *         an envelope with a command to check
     * @return duplicate command error if the command has been recently handled,
     *         {@code Optional.empty()} otherwise
     */
    Optional<Error> check(CommandEnvelope command) {
        if (enabled && didHandleRecently(command)) {
            var errorMessage = format(
                    "Command %s[%s] is a duplicate.",
                    command.messageClass(),
                    command.id().value()
            );
            var error = Error.newBuilder()
                    .setType(CommandValidationError.class.getSimpleName())
                    .setCode(DUPLICATE_COMMAND_VALUE)
                    .setMessage(errorMessage)
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Checks that the event was not dispatched to the aggregate.
     *
     * @param event
     *         an envelope with an event to check
     * @return duplicate event error if the event has been recently handled,
     *         {@code Optional.empty()} otherwise
     */
    Optional<Error> check(EventEnvelope event) {
        if (enabled && didHandleRecently(event)) {
            var errorMessage = format(
                    "Event %s[%s] is a duplicate.",
                    event.messageClass(),
                    event.id().value()
            );
            var error = Error.newBuilder()
                    .setType(EventValidationError.class.getSimpleName())
                    .setCode(DUPLICATE_EVENT_VALUE)
                    .setMessage(errorMessage)
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Checks if the event was already handled by the aggregate recently.
     *
     * <p>The check is performed by searching the {@code historyDepth} most recent
     * events of the aggregate's history for an event caused by this event.
     *
     * <p>This functionality supports the ability to stop duplicate events from being dispatched
     * to the aggregate.
     *
     * @param event
     *         the event to check
     * @return {@code true} if the event was handled within the recent-history window,
     *         {@code false} otherwise
     */
    private boolean didHandleRecently(EventEnvelope event) {
        var eventId = event.id();
        Predicate<Event> causedByEvent = e -> e.context()
                                               .getPastMessage()
                                               .messageId()
                                               .isEvent();
        Predicate<Event> originHasGivenId = e -> e.context()
                                                  .getPastMessage()
                                                  .messageId()
                                                  .asEventId()
                                                  .equals(eventId);
        var found = aggregate.eventHistoryContains(
                historyDepth, causedByEvent.and(originHasGivenId));
        return found;
    }

    /**
     * Checks if the command was already handled by the aggregate recently.
     *
     * <p>The check is performed by searching the {@code historyDepth} most recent
     * events of the aggregate's history for an event caused by this command.
     *
     * <p>This functionality supports the ability to stop duplicate commands from being dispatched
     * to the aggregate.
     *
     * @param command
     *         the command to check
     * @return {@code true} if the command was handled within the recent-history window,
     *         {@code false} otherwise
     */
    private boolean didHandleRecently(CommandEnvelope command) {
        var commandId = command.id();
        Predicate<Event> causedByCommand = e -> e.context()
                                                 .getPastMessage()
                                                 .messageId()
                                                 .isCommand();
        Predicate<Event> originHasGivenId = e -> e.context()
                                                  .getPastMessage()
                                                  .messageId()
                                                  .asCommandId()
                                                  .equals(commandId);
        var found = aggregate.eventHistoryContains(
                historyDepth, causedByCommand.and(originHasGivenId));
        return found;
    }
}
