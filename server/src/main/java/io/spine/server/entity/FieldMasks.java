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

package io.spine.server.entity;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.util.FieldMaskUtil;
import io.spine.validation.NonValidated;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A utility class for creating instances of {@code FieldMask} and processing them
 * against instances of {@link Message}.
 */
public final class FieldMasks {

    /** Prevent instantiation of this utility class. */
    private FieldMasks() {
    }

    /**
     * Applies the given {@code FieldMask} to given collection of {@link Message}s.
     * Does not change the {@link Collection} itself.
     *
     * <p>If the {@code FieldMask} instance contains invalid field declarations, they are
     * ignored and do not affect the execution result.
     *
     * <p>Please note, the returning messages are not validated according to
     * the corresponding {@code Message} constraints. As masking may clear
     * some required fields, the resulting instances are always built with {@code buildPartial()}.
     *
     * @param mask     {@code FieldMask} to apply to each item of the input {@link Collection}.
     * @param messages {@link Message}s to filter.
     * @return messages with the {@code FieldMask} applied
     */
    @Nonnull
    public static <M extends Message>
    Collection<@NonValidated M> applyMask(FieldMask mask, Collection<M> messages) {
        checkNotNull(mask);
        checkNotNull(messages);
        return doApplyMany(mask, messages);
    }

    /**
     * Applies the {@code FieldMask} to the given {@link Message}
     * if the {@code mask} parameter is valid.
     *
     * <p>If the {@code FieldMask} instance contains invalid field declarations,
     * they are ignored and do not affect the execution result.
     *
     * <p>Please note, the returning message is not validated according to
     * the corresponding {@code Message} constraints. As masking may clear
     * some required fields, the resulting instance is always built with {@code buildPartial()}.
     *
     * @param mask    the {@code FieldMask} to apply
     * @param message the {@link Message} to apply given mask to
     * @return the message of the same type as the given one with only selected fields if
     * the {@code mask} is valid, original message otherwise.
     */
    public static <M extends Message> @NonValidated M applyMask(FieldMask mask, M message) {
        checkNotNull(mask);
        checkNotNull(message);
        if (mask.getPathsList()
                .isEmpty()) {
            return message;
        }
        var result = distill(message, mask);
        return result;
    }

    private static <M extends Message> @NonValidated M distill(M wholeMessage, FieldMask mask) {
        var builder = wholeMessage.newBuilderForType();
        FieldMaskUtil.merge(mask, wholeMessage, builder);
        @SuppressWarnings("unchecked") // safe as we got builder of `M`.
        var result = (M) builder.buildPartial();
        return result;
    }

    private static <M extends Message>
    ImmutableList<M> doApplyMany(FieldMask mask, Collection<M> messages) {
        var input = ImmutableList.copyOf(messages);
        if (input.isEmpty()) {
            return input;
        }

        var filter = mask.getPathsList();
        if (filter.isEmpty()) {
            return input;
        }

        ImmutableList.Builder<M> filtered = ImmutableList.builder();
        for (var wholeMessage : messages) {
            var distilled = distill(wholeMessage, mask);
            filtered.add(distilled);
        }
        return filtered.build();
    }
}
