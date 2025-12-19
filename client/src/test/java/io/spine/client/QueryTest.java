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

package io.spine.client;

import io.spine.test.client.TestEntity;
import io.spine.type.KnownTypes;
import io.spine.validation.NonValidated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`Query` should")
class QueryTest {

    private static final String TARGET_ENTITY_TYPE_URL =
            "type.spine.io/spine.test.queries.TestEntity";

    private static final KnownTypes knownTypes = KnownTypes.instance();

    @Test
    void knowTypeProto() {
        var files = knownTypes.fileNames();
        assertThat(files)
                .contains("google/protobuf/type.proto");
    }

    @Test
    void knowEnumValueType() {
        var types = knownTypes.typeNames();
        assertThat(types)
             .contains("google.protobuf.EnumValue");
    }

    @Test
    @DisplayName("obtain entity type url for known query target type")
    void returnTypeUrlForKnownType() {
        var target = Targets.allOf(TestEntity.class);
        var query = Query.newBuilder()
                .setTarget(target)
                .buildPartial();
        var type = query.targetType();
        assertNotNull(type);
        assertEquals(TARGET_ENTITY_TYPE_URL, type.toString());
    }

    @Test
    @DisplayName("throw `IllegalStateException` for unknown query target type")
    void throwErrorForUnknownType() {
        var target = Target.newBuilder()
                .setType("nonexistent/message.type")
                .setIncludeAll(true)
                .build();
        @NonValidated Query query = Query.newBuilder()
                .setTarget(target)
                .buildPartial();
        assertThrows(IllegalStateException.class, query::targetType);
    }
}
