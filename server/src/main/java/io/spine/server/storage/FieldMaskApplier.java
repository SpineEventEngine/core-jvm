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

package io.spine.server.storage;

import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.server.entity.EntityRecord;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.entity.FieldMasks.applyMask;

/**
 * A {@link Function} transforming the storage record
 * by applying the given {@link FieldMask} to it.
 *
 * <p>In case the passed record is an {@link EntityRecord}, it is unpacked,
 * and masking is performed for the packed state.
 * The resulting {@code EntityRecord} is going to have
 * the same fields as the original, except its {@code state} field,
 * transformed via masking.
 *
 * <p>If the passed record is a regular Proto message,
 * a {@linkplain io.spine.server.entity.FieldMasks#applyMask(FieldMask, Message)
 * simple masking procedure} is performed.
 *
 * @param <R>
 *         the type of the record
 */
public final class FieldMaskApplier<R extends Message> implements Function<R, R> {

    private final FieldMask fieldMask;

    public FieldMaskApplier(FieldMask fieldMask) {
        this.fieldMask = fieldMask;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable R apply(@Nullable R input) {
        if (null == input || fieldMask.getPathsList()
                                      .isEmpty()) {
            return input;
        }
        if (input instanceof EntityRecord) {
            return (R) maskEntityRecord((EntityRecord) input);
        }
        return applyMask(fieldMask, input);
    }

    private EntityRecord maskEntityRecord(EntityRecord input) {
        checkNotNull(input);
        var maskedState = maskAny(input.getState());
        var result = EntityRecord.newBuilder(input)
                .setState(maskedState)
                .build();
        return result;
    }

    private Any maskAny(Any message) {
        var stateMessage = unpack(message);
        var maskedMessage = applyMask(fieldMask, stateMessage);
        var result = pack(maskedMessage);
        return result;
    }
}
