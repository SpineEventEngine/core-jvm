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

package io.spine.server.entity;

import io.spine.core.Version;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;
import io.spine.testdata.Sample;

import static io.spine.base.Identifier.newUuid;

public class TestEntity
        extends AbstractEntity<ProjectId, Project>
        implements HasLifecycleColumns<ProjectId, Project> {

    static TestEntity newInstance(String id) {
        var result = new TestEntityBuilder()
                .setResultClass(TestEntity.class)
                .withId(newIdWith(id))
                .build();
        return result;
    }

    static TestEntity withState() {
        var result = new TestEntityBuilder()
                .setResultClass(TestEntity.class)
                .withId(newIdWith(newUuid()))
                .withState(Sample.messageOfType(Project.class))
                .withVersion(3)
                .build();
        return result;
    }

    static TestEntity withStateOf(TestEntity entity) {
        var result = new TestEntityBuilder()
                .setResultClass(TestEntity.class)
                .withId(entity.id())
                .withState(entity.state())
                .withVersion(entity.version()
                                   .getNumber())
                .build();
        return result;
    }

    private static ProjectId newIdWith(String value) {
        return ProjectId.newBuilder()
                        .setId(value)
                        .build();
    }

    private TestEntity(ProjectId id) {
        super(id);
    }

    public static class TestEntityBuilder extends EntityBuilder<TestEntity, ProjectId, Project> {

        @Override
        protected void setState(TestEntity result, Project state, Version version) {
            // NoOp.
        }
    }
}
