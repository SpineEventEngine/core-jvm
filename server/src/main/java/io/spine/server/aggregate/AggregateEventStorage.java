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

package io.spine.server.aggregate;

import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.MessageStorage;
import io.spine.server.storage.StorageFactory;

import java.util.Iterator;

/**
 * Storage of events for each {@link Aggregate}.
 *
 * @see AggregateStorage
 */
public class AggregateEventStorage
        extends MessageStorage<AggregateEventRecordId, AggregateEventRecord> {

    /**
     * A specification on how to store the event records of an aggregate.
     */
    @SuppressWarnings("ConstantConditions")     // Protobuf getters return non-{@code null} values.
    private static final RecordSpec<AggregateEventRecordId, AggregateEventRecord> spec =
            new RecordSpec<>(
                    AggregateEventRecordId.class,
                    AggregateEventRecord.class,
                    AggregateEventRecord::getId,
                    AggregateEventRecordColumn.definitions()
            );

    /**
     * Creates a new storage.
     *
     * <p>Uses the passed factory to create a {@code RecordStorage} delegate, and configures it with
     * the columns stored for the {@link AggregateEventRecord}.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param factory
     *         the storage factory to use when creating a record storage delegate
     */
    public AggregateEventStorage(ContextSpec context, StorageFactory factory) {
        super(context, factory.createRecordStorage(context, spec));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method as a part of storage's package-level API.
     */
    @Override
    protected Iterator<AggregateEventRecord>
    readAll(RecordQuery<AggregateEventRecordId, AggregateEventRecord> query) {
        return super.readAll(query);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method as a part of storage's package-level API.
     */
    @Override
    protected boolean delete(AggregateEventRecordId id) {
        return super.delete(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method as a part of storage's package-level API.
     */
    @Override
    protected void deleteAll(Iterable<AggregateEventRecordId> ids) {
        super.deleteAll(ids);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method is exposed to {@code public} for SPI users.
     */
    @Override
    public void write(AggregateEventRecord record) {
        super.write(record);
    }
}
