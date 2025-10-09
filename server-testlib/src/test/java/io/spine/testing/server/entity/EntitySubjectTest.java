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

package io.spine.testing.server.entity;

import com.google.common.truth.Subject;
import io.spine.server.entity.Entity;
import io.spine.testing.SubjectTest;
import io.spine.testing.server.entity.given.TestEntity;
import io.spine.testing.server.given.entity.TuProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.testing.TestValues.nullRef;

@DisplayName("`EntitySubject` should")
class EntitySubjectTest extends SubjectTest<EntitySubject, Entity<?, ?>> {

    @Override
    protected Subject.Factory<EntitySubject, Entity<?, ?>> subjectFactory() {
        return EntitySubject.entities();
    }

    @Test
    @DisplayName("fail to check flags if entity is `null`")
    void flags() {
        expectSomeFailure(whenTesting -> whenTesting.that(null).archivedFlag());
        expectSomeFailure(whenTesting -> whenTesting.that(null).deletedFlag());
    }

    @Test
    @DisplayName("fail to check state if entity is `null`")
    void state() {
        expectSomeFailure(whenTesting -> whenTesting.that(null).hasStateThat());
    }

    @Test
    @DisplayName("check that the entity does not exist")
    void doesNotExist() {
        assertWithSubjectThat(nullRef()).doesNotExist();
        expectSomeFailure(whenTesting -> whenTesting.that(null).exists());
    }

    @Test
    @DisplayName("check that the entity exists")
    void exists() {
        Entity<?, ?> entity = new TestEntity(id("42"));
        assertWithSubjectThat(entity).exists();
        expectSomeFailure(whenTesting -> whenTesting.that(entity).doesNotExist());
    }

    private static TuProjectId id(String value) {
        return TuProjectId.newBuilder()
                          .setValue(value)
                          .build();
    }
}
