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

package io.spine.server.storage.memory;

import com.google.common.collect.Iterators;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.server.storage.FieldMaskApplier;
import io.spine.server.storage.RecordWithColumns;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.filterValues;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.entity.FieldMasks.applyMask;
import static io.spine.server.storage.memory.RecordComparator.accordingTo;
import static java.util.Collections.synchronizedMap;
import static java.util.stream.Collectors.toList;

/**
 * The memory-based storage for message records.
 *
 * <p>Acts like a facade API for the operations available over the data of a single tenant.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the records
 */
final class TenantRecords<I, R extends Message>
        implements TenantDataStorage<I, RecordWithColumns<I, R>> {

    private final Map<I, RecordWithColumns<I, R>> records = synchronizedMap(new HashMap<>());

    @Override
    public Iterator<I> index() {
        var result = records.keySet().iterator();
        return result;
    }

    /**
     * Obtains the iterator over the identifiers of the records which match the passed query.
     */
    public Iterator<I> index(RecordQuery<I, R> query) {
        var subset = findRecords(query);
        var result = Iterators.transform(subset.iterator(), RecordWithColumns::id);
        return result;
    }

    @Override
    public void put(I id, RecordWithColumns<I, R> record) {
        records.put(id, record);
    }

    /**
     * Returns the message with the passed identifier and applies the given field mask to it.
     *
     * <p>If there is no such a message stored, returns {@code Optional.empty()}.
     */
    public Optional<R> get(I id, FieldMask mask) {
        return get(id).map(r -> new FieldMaskApplier<R>(mask).apply(r.record()));
    }

    @Override
    public Optional<RecordWithColumns<I, R>> get(I id) {
        var record = records.get(id);
        return Optional.ofNullable(record);
    }

    boolean delete(I id) {
        return records.remove(id) != null;
    }

    Iterator<R> readAll(RecordQuery<I, R> query) {
        var fieldMask = query.mask();
        var records = findRecords(query);
        return records.stream()
                .map(RecordWithColumns::record)
                .map(new FieldMaskApplier<>(fieldMask))
                .iterator();
    }

    private List<RecordWithColumns<I, R>> findRecords(RecordQuery<I, R> query) {
        synchronized (records) {
            var filtered = filterRecords(query.subject());
            var stream = filtered.values()
                                                             .stream();
            return sortAndLimit(stream, query).collect(toList());
        }
    }

    private static <I, R extends Message> Stream<RecordWithColumns<I, R>>
    sortAndLimit(Stream<RecordWithColumns<I, R>> data, RecordQuery<I, R> query) {
        var stream = data;
        var sortingSpecs = query.sorting();
        if (sortingSpecs.size() > 0) {
            stream = stream.sorted(accordingTo(sortingSpecs));
        }
        var limit = query.limit();
        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }
        return stream;
    }

    /**
     * Filters the records returning only the ones matching the
     * {@linkplain Subject subject of the record query}.
     */
    private Map<I, RecordWithColumns<I, R>> filterRecords(Subject<I, R> subject) {
        var matcher = new RecordQueryMatcher<>(subject);
        return filterValues(records, matcher::test);
    }

    @Override
    public boolean isEmpty() {
        return records.isEmpty();
    }
}
