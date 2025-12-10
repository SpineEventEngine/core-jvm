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

package io.spine.server.entity.model;

import com.google.common.collect.Range;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;
import io.spine.server.entity.AbstractEntity;
import io.spine.test.model.contexts.tasks.Task;
import io.spine.test.model.contexts.tasks.TaskId;
import io.spine.time.InstantConverter;
import io.spine.testing.time.Past;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EntityClass` should")
class EntityClassTest {

    private final EntityClass<TaskEntity> entityClass =
            new EntityClass<>(TaskEntity.class);

    @Test
    @DisplayName("return ID class")
    void returnIdClass() {
        assertThat(entityClass.idClass())
                .isEqualTo(TaskId.class);
    }

    @Test
    @DisplayName("obtain entity constructor")
    void getEntityConstructor() {
        var ctor = entityClass.constructor();
        assertNotNull(ctor);
    }

    @Test
    @DisplayName("create and initialize entity instance")
    void createEntityInstance() {
        var id = TaskId.generate();
        var before = Past.secondsAgo(1);

        // Create and init the entity.
        var entityClass = new EntityClass<>(TaskEntity.class);
        AbstractEntity<TaskId, Task> entity = entityClass.create(id);

        var after = Time.currentTime();

        // The interval with a much earlier start to allow non-zero interval on faster computers.
        var whileWeCreate = Range.closed(toInstant(before), toInstant(after));

        assertThat(entity.id())
                .isEqualTo(id);
        assertThat(entity.version()
                         .isZero())
                .isTrue();

        var whenModifier = toInstant(entity.whenModified());
        assertTrue(whileWeCreate.contains(whenModifier));

        assertThat(entity.state())
                .isEqualTo(Task.getDefaultInstance());

        assertThat(entity.isArchived())
                .isFalse();
        assertThat(entity.isDeleted())
                .isFalse();
    }

    /** A test entity which defines ID and state. */
    private static class TaskEntity extends AbstractEntity<TaskId, Task> {

        private TaskEntity(TaskId id) {
            super(id);
        }
    }

    static Instant toInstant(Timestamp timestamp) {
        return InstantConverter.instance()
                               .reverse()
                               .convert(timestamp);
    }
}
