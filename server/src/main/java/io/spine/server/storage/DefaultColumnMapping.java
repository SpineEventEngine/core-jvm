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

package io.spine.server.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import org.jspecify.annotations.Nullable;

import static io.spine.server.storage.ColumnTypeMapping.identity;

/**
 * A default column mapping which stores all values "as-is".
 *
 * <p>May be sufficient for in-memory storage implementations and storages that do manual
 * conversion of column values.
 */
public final class DefaultColumnMapping extends AbstractColumnMapping<Object> {

    public static final DefaultColumnMapping INSTANCE = new DefaultColumnMapping();

    /**
     * Prevents external construction so the class is accessed only through the {@link #INSTANCE}.
     */
    private DefaultColumnMapping() {
        super();
    }

    @Override
    protected ColumnTypeMapping<String, String> ofString() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Integer, Integer> ofInteger() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Long, Long> ofLong() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Float, Float> ofFloat() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Double, Double> ofDouble() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Boolean, Boolean> ofBoolean() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<ByteString, ByteString> ofByteString() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Enum<?>, Enum<?>> ofEnum() {
        return identity();
    }

    @Override
    protected ColumnTypeMapping<Message, Message> ofMessage() {
        return identity();
    }

    @Override
    public ColumnTypeMapping<? extends @Nullable Object, ? extends @Nullable Object> ofNull() {
        return o -> null;
    }
}
