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

import io.spine.server.entity.AbstractEntity;
import io.spine.server.entity.DefaultRecordBasedRepository;
import io.spine.server.given.groups.GroupId;
import io.spine.server.given.groups.GroupName;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;

public class DefaultEntityFactoryTestEnv {

    /** Prevents instantiation of this utility class. */
    private DefaultEntityFactoryTestEnv() {
    }

    /** A test entity class which is not versionable. */
    public static class TestEntity1 extends AbstractEntity<ProjectId, Project> {
        private TestEntity1(ProjectId id) {
            super(id);
        }
    }

    /** A test repository. */
    public static class TestRepository1
            extends DefaultRecordBasedRepository<ProjectId, TestEntity1, Project> {
    }

    /** Another entity with the same ID and different state. */
    public static class TestEntity2 extends AbstractEntity<GroupId, GroupName> {
        protected TestEntity2(GroupId id) {
            super(id);
        }
    }

    /** A repository for {@link TestEntity2}. */
    public static class TestRepository2
            extends DefaultRecordBasedRepository<GroupId, TestEntity2, GroupName> {
    }
}
