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

package io.spine.server.migration.mirror.given;

import com.google.protobuf.Message;
import io.spine.base.EntityState;
import io.spine.server.ContextSpec;
import io.spine.server.entity.Entity;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;

/**
 * In-memory {@code StorageFactory} which substitutes individual storages with the custom ones.
 */
public final class PreparedStorageFactory {

    private PreparedStorageFactory() {
    }

    /**
     * Returns in-memory {@code StorageFactory} with the custom {@code EntityRecordStorage}.
     */
    public static StorageFactory with(EntityRecordStorage<?, ?> entityRecordStorage) {
        return new InMemStorageFactory(entityRecordStorage);
    }

    private record InMemStorageFactory(
            EntityRecordStorage<?, ?> entityRecordStorage
    ) implements StorageFactory {

        @Override
        public boolean isOpen() {
            return entityRecordStorage.isOpen();
        }

        @Override
        public void close() {
            entityRecordStorage.close();
        }

        @Override
        public <I, R extends Message> RecordStorage<I, R> createRecordStorage(
                ContextSpec context, RecordSpec<I, R> recordSpec) {
            return InMemoryStorageFactory.newInstance()
                                         .createRecordStorage(context, recordSpec);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I, S extends EntityState<I>> EntityRecordStorage<I, S> createEntityRecordStorage(
                ContextSpec context, Class<? extends Entity<I, S>> entityClass) {
            return (EntityRecordStorage<I, S>) entityRecordStorage;
        }
    }
}
