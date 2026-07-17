/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity.given.repository;

import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.Repository;
import io.spine.server.entity.StorageConverter;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.storage.Storage;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;

import java.util.Optional;

/**
 * A repository built directly on {@link Repository}, bypassing the record-based bases.
 *
 * <p>Since the {@code SignalDispatchingRepository} unification, no framework repository
 * extends {@code Repository} directly, so this fixture keeps the base-level machinery —
 * such as {@linkplain Repository#iterator(java.util.function.Predicate) iteration} —
 * exercised the way a user-defined direct subclass would.
 */
public final class BareRepository extends Repository<ProjectId, ProjectEntity> {

    @Override
    public ProjectEntity create(ProjectId id) {
        return new ProjectEntity(id);
    }

    @Override
    public Optional<ProjectEntity> find(ProjectId id) {
        return records().read(id)
                        .map(record -> create(id));
    }

    @Override
    protected Storage<ProjectId, ?> createStorage() {
        return defaultStorageFactory()
                .createEntityRecordStorage(context().spec(), ProjectEntity.class);
    }

    @Override
    protected void doStore(ProjectEntity entity) {
        EntityRecord record = StorageConverter.toEntityRecord(entity)
                                              .build();
        records().write(entity.id(), record);
    }

    @SuppressWarnings("unchecked") // The storage is created in `createStorage()`.
    private EntityRecordStorage<ProjectId, Project> records() {
        return (EntityRecordStorage<ProjectId, Project>) storage();
    }
}
