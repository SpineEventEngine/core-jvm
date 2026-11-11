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

import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;

import java.util.Iterator;

/**
 * An in-memory implementation of {@link RecordStorage}.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored records
 */
public class InMemoryRecordStorage<I, R extends Message> extends RecordStorage<I, R> {

    private final MultiTenantStorage<TenantRecords<I, R>> multiTenantStorage;

    InMemoryRecordStorage(ContextSpec context, RecordSpec<I, R> recordSpec) {
        super(context, recordSpec);
        this.multiTenantStorage =
                new MultiTenantStorage<>(context.isMultiTenant()) {
                    @Override
                    TenantRecords<I, R> createSlice() {
                        return new TenantRecords<>();
                    }
                };
    }

    private TenantRecords<I, R> records() {
        return multiTenantStorage.currentSlice();
    }

    @Override
    public Iterator<I> index() {
        return records().index();
    }

    @Override
    protected Iterator<I> index(RecordQuery<I, R> query) {
        return records().index(query);
    }

    @Override
    public void write(I id, R record) {
        writeRecord(RecordWithColumns.of(id, record));
    }

    @Override
    protected void writeRecord(RecordWithColumns<I, R> record) {
        records().put(record.id(), record);
    }

    @Override
    protected void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records) {
        for (var record : records) {
            records().put(record.id(), record);
        }
    }

    @Override
    protected Iterator<R> readAllRecords(RecordQuery<I, R> query) {
        return records().readAll(query);
    }

    @Override
    protected boolean deleteRecord(I id) {
        return records().delete(id);
    }
}
