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

package io.spine.server.storage.system.given;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.delivery.CatchUpStorage;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.event.EventStore;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.spine.testing.TestValues.nullRef;

/**
 * A test-only {@link StorageFactory} that always returns {@code null}s instead of storages and
 * memoizes the requested storage types.
 */
public final class MemoizingStorageFactory implements StorageFactory {

    private final List<Class<?>> requestedStorages = new ArrayList<>();
    private boolean requestedInbox = false;
    private boolean requestedCatchUp = false;
    private boolean requestedEventStore = false;
    private boolean closed = false;

    @Override
    public InboxStorage createInboxStorage(boolean multiTenant) {
        requestedInbox = true;
        return nullRef();
    }

    @Override
    public CatchUpStorage createCatchUpStorage(boolean multiTenant) {
        requestedCatchUp = true;
        return nullRef();
    }

    @Override
    public EventStore createEventStore(ContextSpec context) {
        requestedEventStore = true;
        return nullRef();
    }

    @Override
    public <I, M extends Message> RecordStorage<I, M>
    createRecordStorage(ContextSpec context,
                        RecordSpec<I, M> recordSpec, @Nullable StorageGroup group) {
        requestedStorages.add(recordSpec.recordType());
        return nullRef();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    public ImmutableList<Class<?>> requestedStorages() {
        return ImmutableList.copyOf(requestedStorages);
    }

    public boolean requestedInbox() {
        return requestedInbox;
    }

    public boolean requestedCatchUp() {
        return requestedCatchUp;
    }

    public boolean requestedEventStore() {
        return requestedEventStore;
    }

    public boolean isClosed() {
        return closed;
    }
}
