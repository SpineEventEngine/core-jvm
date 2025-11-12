/*
 * Copyright 2022, TeamDev. All rights reserved.
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

package io.spine.core;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.base.EventMessage;
import io.spine.protobuf.Messages;
import io.spine.type.KnownMessage;
import io.spine.validate.FieldAwareMessage;

import java.util.Optional;

import static io.spine.protobuf.Messages.isDefault;

/**
 * Mixin interface for event objects.
 */
@Immutable
interface EventMixin
        extends Signal<EventId, EventMessage, EventContext>, FieldAwareMessage, KnownMessage {

    @Override
    default Timestamp timestamp() {
        return context().getTimestamp();
    }

    /**
     * Obtains the time of the event.
     *
     * @deprecated please use {@link #timestamp()}
     */
    @Deprecated
    default Timestamp time() {
        return timestamp();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Attempts to obtain the ID from the {@code EventContext}. If not successful, assumes that
     * this {@code Event} is the root message.
     */
    @Override
    default MessageId rootMessage() {
        return context()
                .rootMessage()
                .orElseGet(this::messageId);
    }

    @Override
    default Optional<Origin> origin() {
        var parent = context().getPastMessage();
        return Optional.of(parent)
                       .filter(Messages::isNotDefault);
    }

    /**
     * Obtains the ID of the root command, which lead to this event.
     *
     * <p>In case the {@code Event} is a reaction to another {@code Event},
     * the identifier of the very first command in this chain is returned.
     *
     * @return the root command ID
     * @throws IllegalStateException
     *         if the root signal is not a command
     * @deprecated If an event is imported, it does not have a command ID and this method fails.
     *         Use {@link #rootMessage()}.
     */
    @Deprecated
    default CommandId rootCommandId() throws IllegalStateException {
        return context().getPastMessage()
                        .root()
                        .asCommandId();
    }

    /**
     * Checks if this event is a rejection.
     *
     * @return {@code true} if the given event is a rejection, {@code false} otherwise
     */
    default boolean isRejection() {
        var context = context();
        var result = context.hasRejection() || !isDefault(context.getRejection());
        return result;
    }

    /**
     * Creates a copy of this instance without enrichments.
     *
     * <p>Use this method to decrease a size of an event, if enrichments aren't important.
     *
     * <p>A result won't contain:
     * <ul>
     *     <li>the enrichment from the event context;
     *     <li>the enrichment from the first-level origin.
     * </ul>
     *
     * <p>This method does not remove enrichments from second-level and deeper origins to avoid a
     * heavy performance operation.
     *
     * <p>To remove enrichments from the whole parent context hierarchy, use
     * {@link #clearAllEnrichments()}.
     *
     * @return the event without enrichments
     */
    @SuppressWarnings("ClassReferencesSubclass") // `Event` is the only case of this mixin.
    @Internal
    default Event clearEnrichments() {
        return Enrichments.clear((Event) this);
    }

    /**
     * Creates a copy of this instance with enrichments cleared from self and all parent contexts.
     *
     * <p>Use this method to decrease a size of an event, if enrichments aren't important.
     *
     * <p>A result won't contain:
     * <ul>
     *     <li>the enrichment from the event context;
     *     <li>the enrichment from the first-level origin;
     *     <li>the enrichment from the second-level and deeper origins.
     * </ul>
     *
     * <p>This method is performance-heavy.
     *
     * <p>For the "lightweight" version of the method, see {@link #clearEnrichments()}.
     *
     * @return the event without enrichments
     */
    @SuppressWarnings("ClassReferencesSubclass") // `Event` is the only case of this mixin.
    @Internal
    default Event clearAllEnrichments() {
        return Enrichments.clearAll((Event) this);
    }

    /**
     * Obtains the actor context of the event.
     *
     * <p>The {@code ActorContext} is retrieved by traversing {@code Event}s context
     * and can be retrieved from the following places:
     * <ul>
     *     <li>the import context of the event;
     *     <li>the actor context of the command context of this event;
     *     <li>the actor context of the command context of the origin event of any depth.
     * </ul>
     *
     * @return the actor context of the wrapped event
     */
    @Override
    @Internal
    default ActorContext actorContext() {
        var eventContext = context();
        var result = eventContext.actorContext();
        return result;
    }

    @Override
    default MessageId messageId() {
        return identityBuilder()
                .setVersion(context().getVersion())
                .build();
    }

    /**
     * Creates a one-element set with this instance.
     */
    @SuppressWarnings("ClassReferencesSubclass") // which is the only impl.
    default ImmutableSet<Event> toSet() {
        return ImmutableSet.of((Event) this);
    }

    @Override
    @Internal
    default Object readValue(Descriptors.FieldDescriptor field) {
        switch (field.getIndex()) {
            case 0:
                return getId();
            case 1:
                return getMessage();
            case 2:
                return getContext();
            default:
                return getField(field);
        }
    }
}
