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

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.type.TypeUrl;
import io.spine.validate.FieldAwareMessage;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * A mixin interface for the {@link MessageId} type.
 */
@GeneratedMixin
interface MessageIdMixin extends MessageIdOrBuilder, FieldAwareMessage {

    String EVENT_ID_TYPE_URL = TypeUrl.from(EventId.getDescriptor()).value();

    String COMMAND_ID_TYPE_URL = TypeUrl.from(CommandId.getDescriptor()).value();

    /**
     * Obtains the ID of the message.
     */
    default Message id() {
        return unpack(getId());
    }

    /**
     * Checks if the associated message is an event.
     */
    default boolean isEvent() {
        var id = getId();
        return EVENT_ID_TYPE_URL.equals(id.getTypeUrl());
    }

    /**
     * Obtains the {@link EventId} of the associated message.
     *
     * <p>Throws an {@link IllegalStateException} if the associated message is not an event.
     */
    default EventId asEventId() {
        checkState(isEvent(), "%s is not an event ID.", getId().getTypeUrl());
        return unpack(getId(), EventId.class);
    }

    /**
     * Checks if the associated message is a command.
     */
    default boolean isCommand() {
        var id = getId();
        return COMMAND_ID_TYPE_URL.equals(id.getTypeUrl());
    }

    /**
     * Obtains the {@link CommandId} of the associated message.
     *
     * <p>Throws an {@link IllegalStateException} if the associated message is not a command.
     */
    default CommandId asCommandId() {
        checkState(isCommand(), "%s is not a command ID.", getId().getTypeUrl());
        return unpack(getId(), CommandId.class);
    }

    /**
     * Creates a new instance copying ID and type URL from this instance, and applying
     * the passed version.
     */
    @SuppressWarnings("ClassReferencesSubclass") // we want to encapsulate copy construction.
    default MessageId withVersion(Version version) {
        checkNotNull(version);
        return MessageId.newBuilder()
                        .setId(getId())
                        .setTypeUrl(getTypeUrl())
                        .build();
    }

    @Override
    @Internal
    default Object readValue(Descriptors.FieldDescriptor field) {
        switch (field.getIndex()) {
            case 0:
                return getId();
            case 1:
                return getTypeUrl();
            case 2:
                return getVersion();
            default:
                return getField(field);
        }
    }
}
