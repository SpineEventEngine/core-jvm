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

package io.spine.server.storage.memory;

import com.google.common.collect.Iterators;
import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.server.storage.RecordWithColumns;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.Maps.filterValues;
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
     * Obtains the iterator over the identifiers of the records that match the passed query.
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

    @Override
    public Optional<RecordWithColumns<I, R>> get(I id) {
        var record = records.get(id);
        return Optional.ofNullable(record);
    }

    boolean delete(I id) {
        return records.remove(id) != null;
    }

    /**
     * Reads the records matching the passed query.
     *
     * <p>The field mask of the query is ignored, as field masks are no longer supported.
     */
    Iterator<R> readAll(RecordQuery<I, R> query) {
        var records = findRecords(query);
        return records.stream()
                .map(RecordWithColumns::record)
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
