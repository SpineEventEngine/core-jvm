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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Timestamp;
import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.validation.FieldAwareMessage;

/**
 * A mixin interface for the {@link Version} message type.
 */
@GeneratedMixin
public interface VersionMixin extends VersionOrBuilder, FieldAwareMessage, WithTime {

    /** Obtains the number part of the version. */
    default int number() {
        return getNumber();
    }

    @Override
    default Timestamp timestamp() {
        return getTimestamp();
    }

    /** Verifies if this version is zero. */
    default boolean isZero() {
        return number() == 0;
    }

    /** Checks if the version is an increment relative to the {@code other} version. */
    default boolean isIncrement(VersionOrBuilder other) {
        return number() > other.getNumber();
    }

    /** Checks if the version is an increment or at least equal to the {@code other} version. */
    default boolean isIncrementOrEqual(VersionOrBuilder other) {
        return number() >= other.getNumber();
    }

    /** Checks if the version is a decrement relative to the {@code other} version. */
    default boolean isDecrement(VersionOrBuilder other) {
        return number() < other.getNumber();
    }

    /** Checks if the version is a decrement or at least equal to the {@code other} version. */
    default boolean isDecrementOrEqual(VersionOrBuilder other) {
        return number() <= other.getNumber();
    }

    @Internal
    @Override
    default Object readValue(FieldDescriptor field) {
        switch (field.getIndex()) {
            case 0:
                return getNumber();
            case 1:
                return getTimestamp();
            default:
                return getField(field);
        }
    }
}
