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

package io.spine.server;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.core.MessageId;
import io.spine.type.TypeUrl;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * Utilities for exposing identity of a server-side object as {@link io.spine.core.MessageId}s.
 */
@Internal
public final class Identity {

    private static final TypeUrl EMPTY_URL = TypeUrl.of(Empty.class);

    /** Prevents instantiation of this utility class. */
    private Identity() {
    }

    /**
     * Creates the ID which has only the passed value.
     *
     * <p>The returned ID does not contain the type information.
     */
    public static MessageId byString(String value) {
        checkNotEmptyOrBlank(value);
        var producer = Identifier.pack(value);
        return ofProducer(producer);
    }

    /**
     * Creates the ID which has only fully qualified name of the passed class.
     *
     * <p>The returned ID does not contain the type information.
     */
    public static MessageId ofSingleton(Class<?> singletonClass) {
        checkNotNull(singletonClass);
        return byString(singletonClass.getName());
    }

    /**
     * Creates the identity by the passed producer ID.
     *
     * <p>The returned ID does not contain the type information.
     */
    public static MessageId ofProducer(Any producerId) {
        checkNotNull(producerId);
        return MessageId
                .newBuilder()
                .setId(producerId)
                .setTypeUrl(EMPTY_URL.value())
                .build();
    }

    /**
     * Creates the identity of the entity state by its ID and type URL.
     */
    public static MessageId ofEntity(Object entityId, TypeUrl entityType) {
        checkNotNull(entityId);
        checkNotNull(entityType);
        return MessageId
                .newBuilder()
                .setId(Identifier.pack(entityId))
                .setTypeUrl(entityType.value())
                .build();
    }
}
