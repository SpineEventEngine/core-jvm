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

package io.spine.server.model;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.model.ArgumentFilter.acceptingAll;

/**
 * Provides information for dispatching a message to a handler method.
 */
@Immutable
public final class DispatchKey {

    private final Class<? extends Message> messageClass;
    private final @Nullable ArgumentFilter filter;
    private final @Nullable Class<? extends Message> originClass;

    /**
     * Creates a new {@code DispatchKey} with the given message class and origin class.
     */
    public DispatchKey(Class<? extends Message> messageClass,
                       @Nullable Class<? extends Message> originClass) {
        this(messageClass, acceptingAll(), originClass);
    }

    /**
     * Creates a new {@code DispatchKey} with the given message class, origin class, and filter.
     */
    DispatchKey(Class<? extends Message> messageClass,
                ArgumentFilter filter,
                @Nullable Class<? extends Message> originClass) {
        checkNotNull(messageClass);
        checkNotNull(filter);
        this.messageClass = messageClass;
        this.filter = filter.acceptsAll() ? null : filter;
        this.originClass = originClass;
    }

    /**
     * Obtains a filter-less version of this dispatch key.
     *
     * <p>If this key has a filter, a new instance is created, which copies this key data
     * without the filter. Otherwise, this instance is returned.
     */
    DispatchKey withoutFilter() {
        if (filter == null) {
            return this;
        }
        return new DispatchKey(messageClass, originClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageClass, filter, originClass);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final var other = (DispatchKey) obj;
        return Objects.equals(this.messageClass, other.messageClass)
                && Objects.equals(this.filter, other.filter)
                && Objects.equals(this.originClass, other.originClass);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")   // Both classes have `filter` field.
    @Override
    public String toString() {
        var helper = MoreObjects.toStringHelper(this);
        helper.add("messageClass", messageClass.getName());
        if (filter != null && !filter.acceptsAll()) {
            helper.add("filter", filter);
        }
        if (originClass != null) {
            helper.add("originClass", originClass);
        }
        return helper.toString();
    }
}
