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

package io.spine.core;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.base.EventMessage;
import io.spine.protobuf.Messages;
import io.spine.type.KnownMessage;
import io.spine.validation.FieldAwareMessage;

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
     * Obtains the ID of the root command, which led to this event.
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
        return switch (field.getIndex()) {
            case 0 -> getId();
            case 1 -> getMessage();
            case 2 -> getContext();
            default -> getField(field);
        };
    }
}
