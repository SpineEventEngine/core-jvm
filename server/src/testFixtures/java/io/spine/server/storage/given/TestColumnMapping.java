/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.storage.given;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.spine.server.storage.AbstractColumnMapping;
import io.spine.server.storage.ColumnTypeMapping;
import io.spine.test.entity.TaskView;
import org.jspecify.annotations.Nullable;

/**
 * Defines a mapping where all types are mapped to a {@code String}.
 */
public final class TestColumnMapping extends AbstractColumnMapping<String> {

    public static final String CONVERTED_STRING = "123";
    public static final String CONVERTED_MESSAGE = "converted-message";
    public static final String NULL_VALUE = "the-null";

    @Override
    protected void setupCustomMapping(
            ImmutableMap.Builder<Class<?>, ColumnTypeMapping<?, ? extends String>> builder) {
        builder.put(TaskView.class, ofTaskView());
        builder.put(IntIdentifier.class, ofIntIdentifier());
    }

    @Override
    protected ColumnTypeMapping<String, String> ofString() {
        return str -> CONVERTED_STRING;
    }

    @Override
    protected ColumnTypeMapping<Integer, ? extends String> ofInteger() {
        return String::valueOf;
    }

    @Override
    protected ColumnTypeMapping<Long, ? extends String> ofLong() {
        throw unsupportedType(Long.class);
    }

    @Override
    protected ColumnTypeMapping<Float, ? extends String> ofFloat() {
        throw unsupportedType(Float.class);
    }

    @Override
    protected ColumnTypeMapping<Double, ? extends String> ofDouble() {
        throw unsupportedType(Double.class);
    }

    @Override
    protected ColumnTypeMapping<Boolean, ? extends String> ofBoolean() {
        throw unsupportedType(Boolean.class);
    }

    @Override
    protected ColumnTypeMapping<ByteString, ? extends String> ofByteString() {
        throw unsupportedType(ByteString.class);
    }

    @Override
    protected ColumnTypeMapping<Enum<?>, ? extends String> ofEnum() {
        throw unsupportedType(Enum.class);
    }

    @Override
    protected ColumnTypeMapping<Message, String> ofMessage() {
        return msg -> CONVERTED_MESSAGE;
    }

    @Override
    public ColumnTypeMapping<? extends @Nullable Object, ? extends String> ofNull() {
        return o -> NULL_VALUE;
    }

    private static ColumnTypeMapping<TaskView, String> ofTaskView() {
        return TaskView::getName;
    }

    @SuppressWarnings("UnnecessaryLambda")
    private static ColumnTypeMapping<IntIdentifier, String> ofIntIdentifier() {
        return id -> String.valueOf(id.getId());
    }
}
