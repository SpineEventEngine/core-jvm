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

import com.google.common.collect.Lists;
import io.spine.base.EntityState;
import io.spine.client.ArchivedColumn;
import io.spine.client.DeletedColumn;
import io.spine.query.RecordColumn;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordStorage;

import java.util.List;

/**
 * Fetches and exposes entity records from the {@link EntityRecordStorage}.
 *
 * @param <I>
 *         the type of the identifiers of stored entities
 * @param <S>
 *         the type of {@code Entity} state
 */
final class FetchedEntityRecords<I, S extends EntityState<I>> {

    private final RecordColumn<EntityRecord, Boolean> archived = RecordColumn.create(
                    ArchivedColumn.instance().name().value(),
                    Boolean.class,
                    EntityRecord::isArchived
            );
    private final RecordColumn<EntityRecord, Boolean> deleted = RecordColumn.create(
                    DeletedColumn.instance().name().value(),
                    Boolean.class,
                    EntityRecord::isDeleted
            );
    private final EntityRecordStorage<I, S> storage;

    /**
     * Creates a new instance of {@code FetchedEntityRecords} using the passed storage
     * as a source of records.
     */
    FetchedEntityRecords(EntityRecordStorage<I, S> storage) {
        this.storage = storage;
    }

    /**
     * Returns entity records, which are neither archived nor deleted.
     */
    List<EntityRecord> active() {
        var iterator = storage.readAll();
        var result = Lists.newArrayList(iterator);
        return result;
    }

    /**
     * Return entity records, which are marked as archived.
     */
    List<EntityRecord> archived() {
        var iterator = storage.readAll(
                storage.queryBuilder()
                       .where(archived).is(true)
                       .build()
        );
        var result = Lists.newArrayList(iterator);
        return result;
    }

    /**
     * Return entity records, which are marked as deleted.
     */
    List<EntityRecord> deleted() {
        var iterator = storage.readAll(
                storage.queryBuilder()
                       .where(deleted).is(true)
                       .build()
        );
        var result = Lists.newArrayList(iterator);
        return result;
    }
}
