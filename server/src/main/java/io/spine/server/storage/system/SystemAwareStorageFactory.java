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

package io.spine.server.storage.system;

import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.server.ContextSpec;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateStorage;
import io.spine.server.delivery.CatchUpStorage;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.event.EventStore;
import io.spine.server.event.store.EmptyEventStore;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of {@link StorageFactory} which may produce an empty {@link EventStore} for
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
     * @return a new {@code SystemAwareStorageFactory} of the given {@code factory} if it is
     *         an instance of {@code SystemAwareStorageFactory}
     */
    public static SystemAwareStorageFactory wrap(StorageFactory factory) {
        checkNotNull(factory);
        return factory instanceof SystemAwareStorageFactory
               ? (SystemAwareStorageFactory) factory
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
    public <I, S extends EntityState<I>> AggregateStorage<I, S>
    createAggregateStorage(ContextSpec context,
                           Class<? extends Aggregate<I, S, ?>> aggregateClass) {
        return delegate.createAggregateStorage(context, aggregateClass);
    }

    @Override
    public InboxStorage createInboxStorage(boolean multitenant) {
        return delegate.createInboxStorage(multitenant);
    }

    @Override
    public CatchUpStorage createCatchUpStorage(boolean multitenant) {
        return delegate.createCatchUpStorage(multitenant);
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
    createRecordStorage(ContextSpec context, RecordSpec<I, M> recordSpec) {
        return delegate.createRecordStorage(context, recordSpec);
    }

    /**
     * Closes the associated delegate factory.
     */
    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
