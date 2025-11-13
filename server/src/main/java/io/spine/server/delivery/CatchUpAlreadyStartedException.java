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

package io.spine.server.delivery;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Set;

import static java.lang.String.format;

/**
 * An exception telling that the projection catch-up cannot be started, since some of the requested
 * entities are already catching up.
 */
public final class CatchUpAlreadyStartedException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 0L;

    private final TypeUrl projectionStateType;
    private final @Nullable ImmutableSet<Object> requestedIds;

    CatchUpAlreadyStartedException(TypeUrl type, @Nullable Set<?> ids) {
        super();
        projectionStateType = type;
        requestedIds = ids == null ? null : ImmutableSet.copyOf(ids);
    }

    @Override
    public String getMessage() {
        var message = format(
                "Cannot start the catch-up for the `%s` Projection, `%s`. " +
                        "Another catch-up is already in progress.",
                projectionStateType, targetsAsString());
        return message;
    }

    /**
     * Returns the type URL of the projection for which the catch-up was requested.
     */
    public TypeUrl projectionStateType() {
        return projectionStateType;
    }

    /**
     * Returns the IDs of the targets which were asked to catch up.
     *
     * <p>If all the projection entities were specified as a target, returns an empty {@code Set}.
     */
    public ImmutableSet<Object> requestedIds() {
        return requestedIds == null? ImmutableSet.of() : requestedIds;
    }

    private String targetsAsString() {
        if (requestedIds == null) {
            return "[all instances]";
        }
        return Joiner.on(',')
                     .join(requestedIds);
    }
}
