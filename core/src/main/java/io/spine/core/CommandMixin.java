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

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Timestamp;
import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.base.CommandMessage;
import io.spine.protobuf.Messages;
import io.spine.validation.FieldAwareMessage;

import java.util.Optional;

import static io.spine.protobuf.Messages.isNotDefault;

/**
 * Mixin interface for command objects.
 */
@GeneratedMixin
@Immutable
interface CommandMixin
        extends Signal<CommandId, CommandMessage, CommandContext>,
                CommandOrBuilder,
                FieldAwareMessage {

    /**
     * Obtains the time when the command was created.
     */
    @Override
    default Timestamp timestamp() {
        return actorContext().getTimestamp();
    }

    /**
     * Obtains the time when the command was created.
     *
     * @deprecated please use {@link #timestamp()}
     */
    @Deprecated
    default Timestamp time() {
        return timestamp();
    }

    @Override
    default ActorContext actorContext() {
        return context().getActorContext();
    }

    @Override
    default MessageId rootMessage() {
        var rootId = context().getOrigin().root();
        return isNotDefault(rootId)
               ? rootId
               : messageId();
    }

    @Override
    default Optional<Origin> origin() {
        var parent = context().getOrigin();
        return Optional.of(parent)
                       .filter(Messages::isNotDefault);
    }

    @Internal
    @Override
    default Object readValue(Descriptors.FieldDescriptor field) {
        return switch (field.getIndex()) {
            case 0 -> getId();
            case 1 -> getMessage();
            case 2 -> getContext();
            default -> getField(field);
        };
    }
}
