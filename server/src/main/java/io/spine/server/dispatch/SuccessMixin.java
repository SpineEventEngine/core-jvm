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

package io.spine.server.dispatch;

import com.google.protobuf.Descriptors.FieldDescriptor;
import io.spine.annotation.GeneratedMixin;
import io.spine.validation.FieldAwareMessage;

/**
 * A mixin interface for the {@link Success} message type.
 */
@GeneratedMixin
interface SuccessMixin extends SuccessOrBuilder, FieldAwareMessage {

    @Override
    default Object readValue(FieldDescriptor field) {
        switch (field.getIndex()) {
            case 0:
                return getProducedEvents();
            case 1:
                return getProducedCommands();
            case 2:
                return getRejection();
            default:
                return getField(field);
        }
    }

    /**
     * Determines if the outcome has any produced events.
     *
     * @implNote Prefer using this method over the generated {@code hasProducedEvents}
     *         while the latter only checks if the message is set.
     */
    default boolean hasEvents() {
        return hasProducedEvents() && getProducedEvents().getEventCount() > 0;
    }

    /**
     * Determines if the outcome has any produced commands.
     *
     * @implNote Prefer using this method over the generated {@code hasProducedCommands}
     *         while the latter only checks if the message is set.
     */
    default boolean hasCommands() {
        return hasProducedCommands() && getProducedCommands().getCommandCount() > 0;
    }
}
