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

package io.spine.server.storage.system;

import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.server.ContextSpec;
import io.spine.server.delivery.CatchUpStorage;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.event.EventStore;
import io.spine.server.event.store.EmptyEventStore;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageGroup;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of {@link StorageFactory} that may produce an empty {@link EventStore} for
 * system context.
 *
 * <p>Delegates storage creation to another factory.
 *
 * <p>When asked for a {@link EventStore}, creates an {@link EmptyEventStore} if the given context
 * does not store events. Otherwise, delegates to the associated delegate factory.
 */
@Internal
public final class SystemAwareStorageFactory implements StorageFactory {

    private final StorageFactory delegate;

    private SystemAwareStorageFactory(StorageFactory delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps the given factory into a {@code SystemAwareStorageFactory}.
     *
     * @param factory
     *         the {@link StorageFactory} to use as the delegate for storage construction
     * @return the given {@code factory} itself if it is already an instance of
     *         {@code SystemAwareStorageFactory}, or a new {@code SystemAwareStorageFactory}
     *         wrapping it otherwise
     */
    public static SystemAwareStorageFactory wrap(StorageFactory factory) {
        checkNotNull(factory);
        return factory instanceof SystemAwareStorageFactory systemAware
               ? systemAware
               : new SystemAwareStorageFactory(factory);
    }

    /**
     * Obtains the wrapped factory.
     */
    @VisibleForTesting
    public StorageFactory delegate() {
        return delegate;
    }

    @Override
    public InboxStorage createInboxStorage(boolean multiTenant) {
        return delegate.createInboxStorage(multiTenant);
    }

    @Override
    public CatchUpStorage createCatchUpStorage(boolean multiTenant) {
        return delegate.createCatchUpStorage(multiTenant);
    }

    /**
     * Creates a new {@link EventStore}.
     *
     * <p>If the given context does not store events, returns an {@link EmptyEventStore}. Otherwise,
     * delegates to the wrapped instance.
     */
    @Override
    public EventStore createEventStore(ContextSpec context) {
        return context.storesEvents()
               ? delegate.createEventStore(context)
               : new EmptyEventStore();
    }

    @Override
    public <I, M extends Message> RecordStorage<I, M>
    createRecordStorage(ContextSpec context,
                        RecordSpec<I, M> recordSpec,
                        @Nullable StorageGroup group) {
        return delegate.createRecordStorage(context, recordSpec, group);
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    /**
     * Closes the associated delegate factory.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
