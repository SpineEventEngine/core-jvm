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

package io.spine.server.aggregate;

import io.spine.annotation.SPI;
import io.spine.base.AggregateState;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.query.EntityQuery;
import io.spine.server.ContextSpec;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.ToEntityRecordQuery;
import io.spine.server.storage.QueryConverter;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;

import java.util.Iterator;

import static io.spine.server.storage.QueryConverter.convert;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A storage of the latest states of the Aggregates.
 *
 * <p>The state of an Aggregate is persisted on each update, regardless of the
 * {@linkplain io.spine.server.entity.EntityVisibility visibility} of the Aggregate state type.
 * This storage {@code extends} {@link EntityRecordStorage}, so the latest states are persisted
 * as {@link EntityRecord}s and all state-related operations are served by the inherited
 * record storage. It is the source of truth for loading an Aggregate by its
 * {@link AggregateRepository}.
 *
 * <p>Since the event-sourcing cutover, the events emitted by the Aggregates are journaled
 * separately: the journal is an {@link io.spine.server.entity.storage.EntityEventStorage} owned
 * by the {@link AggregateRepository}, not by this storage. The states kept here are not restored
 * from those events.
 *
 * <p>End-users of the framework are able to set the visibility level for each Aggregate state
 * by using an {@linkplain io.spine.server.entity.EntityVisibility (entity).visibility} option
 * in the Protobuf message corresponding to the Aggregate state. For those Aggregates that are
 * visible, the framework routines {@linkplain #enableStateQuerying() enable this storage}
 * to expose the latest states of Aggregates for further
 * {@linkplain #readStates(TargetFilters, ResponseFormat) querying}. To some extent, it makes this
 * storage a part of an application's read-side. Visibility gates only this query exposure —
 * never the state {@linkplain #writeState(Aggregate) writes} or the
 * {@linkplain #read(Object) load-path reads}.
 *
 * <p>{@code AggregateStorage} supports querying the Aggregate states by the values of their
 * declared entity columns. See {@code io.spine.query} package docs for more details on
 * the query language.
 *
 * @param <I>
 *         the type of IDs of aggregates served by this storage
 * @param <S>
 *         the type of states of aggregates served by this storage
 */
@SPI
public class AggregateStorage<I, S extends AggregateState<I>>
        extends EntityRecordStorage<I, S> {

    /**
     * Tells whether the latest aggregate states should be stored and exposed for querying.
     */
    private boolean queryingEnabled = false;

    /**
     * Creates an instance of the storage for a certain aggregate class registered
     * in a specific context.
     *
     * @param context
     *         the specification of the context within which this storage is being created
     * @param aggregateClass
     *         the class of stored aggregates
     * @param factory
     *         a storage factory to create the underlying record storage
     */
    public AggregateStorage(ContextSpec context,
                            Class<? extends Aggregate<I, S, ?>> aggregateClass,
                            StorageFactory factory) {
        super(context, factory, aggregateClass);
    }

    /**
     * Enables the querying of the latest Aggregate states persisted by this storage.
     */
    void enableStateQuerying() {
        queryingEnabled = true;
    }

    /**
     * Queries the storage for the Aggregate states according to the passed filters and returns
     * the results in the specified response format.
     *
     * <p>The results of this call are eventually consistent with the latest states of aggregates,
     * as instances are <em>not</em> restored from their events for querying.
     * Instead, this method works on top of the storage of the latest known aggregate states,
     * for better performance. In a distributed environment, the records in this storage may be
     * outdated, as new events may have been emitted by an aggregate instance on other server nodes.
     *
     * @param filters
     *         the filters to use when querying the aggregate states
     * @param format
     *         the format of the response
     * @return an iterator over the matching {@code EntityRecord}s
     */
    protected Iterator<EntityRecord> readStates(TargetFilters filters, ResponseFormat format) {
        ensureStatesQueryable();
        var query = convert(filters, format, recordSpec());
        var result = readAll(query);
        return result;
    }

    /**
     * Reads the aggregate states from the storage and returns the results in the specified format.
     *
     * <p>This method performs no filtering. Other than that, it works in the same manner
     * as {@link #readStates(TargetFilters, ResponseFormat) readStates(filters, format)}.
     *
     * @param format
     *         the format of the response
     * @return an iterator over the records
     */
    protected Iterator<EntityRecord> readStates(ResponseFormat format) {
        ensureStatesQueryable();
        var query = QueryConverter.newQuery(recordSpec(), format);
        var result = readAll(query);
        return result;
    }

    /**
     * Reads the aggregate states from the storage according to the specified query.
     *
     * @param query
     *         the query to execute
     * @return an iterator over the records
     */
    protected Iterator<EntityRecord> readStates(EntityQuery<I, S, ?> query) {
        ensureStatesQueryable();
        var recordQuery = ToEntityRecordQuery.transform(query);
        var result = readAll(recordQuery);
        return result;
    }

    private void ensureStatesQueryable() {
        if (!queryingEnabled) {
            throw newIllegalStateException(
                    "The storage of Aggregate of type `%s` is not configured to expose " +
                            "the latest Aggregate states for querying. " +
                            "Check the entity visibility level of the Aggregate.",
                    stateClass());
        }
    }

    protected void writeState(Aggregate<I, ?, ?> aggregate) {
        var record = aggregate.toRecord();
        var result = RecordWithColumns.create(record, recordSpec());
        write(result);
    }
}
