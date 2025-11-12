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

package io.spine.server.dispatch;

import io.spine.base.Identifier;
import io.spine.core.MessageId;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.SignalEnvelope;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * A utility for working with {@link DispatchOutcome} instances.
 */
public final class DispatchOutcomes {

    /**
     * Prevents this utility from instantiation.
     */
    private DispatchOutcomes() {
    }

    /**
     * Returns an outcome, which tells about a successful dispatching of the message
     * with the passed ID.
     *
     * @param messageId
     *         identifier of the message
     */
    public static DispatchOutcome successfulOutcome(MessageId messageId) {
        checkNotNull(messageId);
        return withMessageId(messageId)
                .setSuccess(Success.getDefaultInstance())
                .build();
    }

    private static DispatchOutcome.Builder withMessageId(MessageId messageId) {
        return DispatchOutcome
                .newBuilder()
                .setPropagatedSignal(messageId);
    }

    /**
     * Returns an outcome, which tells about a successful dispatching
     * of the passed event envelope.
     *
     * @param event
     *         envelope of the event which was successfully dispatched
     */
    public static DispatchOutcome successfulOutcome(EventEnvelope event) {
        checkNotNull(event);
        return successfulOutcome(event.messageId());
    }

    /**
     * Returns an outcome which tells about a successful dispatching
     * of the passed command envelope.
     *
     * @param command
     *         envelope of the command which was successfully dispatched
     */
    public static DispatchOutcome successfulOutcome(CommandEnvelope command) {
        checkNotNull(command);
        return successfulOutcome(command.messageId());
    }

    /**
     * Returns an outcome, which tells that the passed signal
     * was sent to the inbox of the entity with the passed ID.
     *
     * @param signal
     *         the signal which has been sent to the inbox
     * @param entityId
     *         identifier of the entity, to which inbox the signal
     *         has been sent
     */
    public static <I> DispatchOutcome sentToInbox(SignalEnvelope<?, ?, ?> signal, I entityId) {
        checkNotNull(signal);
        checkNotNull(entityId);

        var addresses = inboxAddressOf(entityId);
        var outcome = withMessageId(signal.messageId())
                .setSentToInbox(addresses)
                .build();
        return outcome;
    }

    /**
     * Returns an outcome, which tells that the passed signal
     * was intended to be sent to the inbox of the entity with the passed ID.
     *
     * <p>If the passed {@code Optional} of entity ID is not present,
     * returns an outcome telling there were
     * {@linkplain #noTargetsToRoute(SignalEnvelope) no targets} to route the signal to.
     *
     * @param signal
     *         the signal which has been sent to the inbox
     * @param entityId
     *         an optional identifier of the entity, to which inbox the signal
     *         should have been sent
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType" /* Handling `Optional` uniformly. */)
    public static <I>
    DispatchOutcome maybeSentToInbox(SignalEnvelope<?, ?, ?> signal, Optional<I> entityId) {
        checkNotNull(signal);
        checkNotNull(entityId);

        return entityId.map(id -> sentToInbox(signal, id))
                       .orElseGet(() -> noTargetsToRoute(signal));
    }

    /**
     * Returns an outcome, which tells that the passed event
     * was sent to several inboxes of entities with the passed identifiers.
     *
     * <p>In case an empty set of entity IDs was passed, returns an outcome
     * telling there were {@linkplain #noTargetsToRoute(SignalEnvelope) no targets}
     * to route the signal to.
     *
     * @param event
     *         the event which has been sent to inboxes
     * @param entityIds
     *         identifiers of entities to which inboxes the event
     *         has been sent
     */
    public static <I> DispatchOutcome sentToInbox(EventEnvelope event, Set<I> entityIds) {
        checkNotNull(event);
        checkNotNull(entityIds);

        if (entityIds.isEmpty()) {
            return noTargetsToRoute(event);
        }

        var addresses = inboxAddressesOf(entityIds);
        var outcome = withMessageId(event.messageId())
                .setSentToInbox(addresses)
                .build();
        return outcome;
    }

    private static <I> InboxAddresses inboxAddressOf(I entityId) {
        var address = InboxAddresses
                .newBuilder()
                .addId(Identifier.pack(entityId))
                .build();
        return address;
    }

    private static <I> InboxAddresses inboxAddressesOf(Set<I> entityIds) {
        var builder = InboxAddresses.newBuilder();
        for (var id : entityIds) {
            var packed = Identifier.pack(id);
            builder.addId(packed);
        }
        return builder.build();
    }

    /**
     * Returns an outcome signifying that the given event has been published
     * to a remote channel, most likely, to another Bounded Context.
     *
     * @param event
     *         the event which has been published
     */
    public static DispatchOutcome publishedToRemote(EventEnvelope event) {
        checkNotNull(event);
        var outcome = withMessageId(event.messageId())
                .setPublishedToRemote(true)
                .build();
        return outcome;
    }

    /**
     * Returns an outcome telling that during the dispatching, there were no target entities
     * to route the signal to.
     *
     * @param signal
     *         the dispatched signal
     */
    private static DispatchOutcome noTargetsToRoute(SignalEnvelope<?, ?, ?> signal) {
        checkNotNull(signal);
        var outcome = withMessageId(signal.messageId())
                .setNoTargetsToRoute(true)
                .build();
        return outcome;
    }

    /**
     * Returns an outcome telling that the event was ignored by the target.
     *
     * <p>Such a scenario is typical for cases in which a target has a filter set
     * for incoming events, which the event does not pass.
     *
     * @param reason
     *         why the event was ignored
     */
    public static DispatchOutcome ignored(EventEnvelope event, String reason) {
        checkNotNull(event);
        checkNotEmptyOrBlank(reason);
        var ignore = Ignore.newBuilder()
                .setReason(reason)
                .build();
        var outcome = withMessageId(event.messageId())
                .setIgnored(ignore)
                .build();
        return outcome;
    }
}
