/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
        return storageFactory()
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
