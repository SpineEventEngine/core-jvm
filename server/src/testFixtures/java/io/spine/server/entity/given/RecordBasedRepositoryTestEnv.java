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

package io.spine.server.entity.given;

import com.google.protobuf.FieldMask;
import io.spine.client.OrderBy;
import io.spine.client.ResponseFormat;
import io.spine.server.entity.AbstractEntity;

import static io.spine.testing.Assertions.assertMatchesMask;

public final class RecordBasedRepositoryTestEnv {

    private static final String ENTITY_NAME_COLUMN = "name";

    /** Prevents instantiation of this test environment class. */
    private RecordBasedRepositoryTestEnv() {
    }

    public static <E extends AbstractEntity<?, ?>>
    void assertMatches(E entity, FieldMask fieldMask) {
        var state = entity.state();
        assertMatchesMask(state, fieldMask);
    }

    /**
     * An order by {@linkplain #ENTITY_NAME_COLUMN entity name column}.
     */
    public static OrderBy orderByName(OrderBy.Direction direction) {
        return OrderBy.newBuilder()
                .setColumn(ENTITY_NAME_COLUMN)
                .setDirection(direction)
                .build();
    }

    public static ResponseFormat emptyFormat() {
        return ResponseFormat.getDefaultInstance();
    }
}
