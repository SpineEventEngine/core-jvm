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

package io.spine.client;

import com.google.protobuf.Any;
import io.spine.base.Identifier;
import io.spine.protobuf.AnyPacker;

import java.util.function.Consumer;

/**
 * Consumer-delegate of entity IDs, which correspond to entities no longer matching
 * the subscription criteria.
 *
 * <p>Unpacks the ID values from the passed {@code Any} instances according
 * to the identifier type passed. It is a responsibility of callees to provide
 * the relevant type of identifiers.
 *
 * @param <I>
 *         the type of entity identifiers
 */
final class NoLongerMatchingConsumer<I> implements Consumer<Any> {

    private final Class<I> idClass;
    private final Consumer<I> delegate;

    /**
     * Creates a new instance of {@code IdConsumer}.
     *
     * @param idClass
     *         the type of identifiers used to unpack the incoming {@code Any} instances
     * @param delegate
     *         the consumer to delegate the observation to
     */
    NoLongerMatchingConsumer(Class<I> idClass, Consumer<I> delegate) {
        this.idClass = idClass;
        this.delegate = delegate;
    }

    @Override
    public void accept(Any packedId) {
        var entityId = AnyPacker.unpack(packedId, EntityId.class);
        var any = entityId.getId();
        var unpacked = Identifier.unpack(any, idClass);
        delegate.accept(unpacked);
    }
}
