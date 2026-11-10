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

package io.spine.server.tenant;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Message;
import io.spine.annotation.SPI;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.TenantId;
import io.spine.server.ContextSpec;
import io.spine.server.storage.MessageStorage;
import io.spine.server.storage.RecordStorage;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract base for a storage holding the information about tenants.
 *
 * @param <T>
 *         the type of data associated with the tenant ID
 */
@SPI
public abstract class TenantStorage<T extends Message>
        extends MessageStorage<TenantId, T>
        implements TenantIndex {

    private final Set<TenantId> cache = Sets.newConcurrentHashSet();

    protected TenantStorage(ContextSpec context, RecordStorage<TenantId, T> delegate) {
        super(context, delegate);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If there is an entity with the passed ID, the method quits. Otherwise,
     * a new entity with the default state will be created and stored.
     *
     * @param id
     *         the tenant ID to store
     */
    @Override
    public final void keep(TenantId id) {
        if (cache.contains(id)) {
            return;
        }

        var optional = read(id);
        if (optional.isEmpty()) {
            var newRecord = create(id);
            write(newRecord);
        }
        cache(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Answers from the cache of known tenants, or reads the record of the tenant.
     * Only a tenant that is found is cached.
     */
    @Override
    public final boolean contains(TenantId id) {
        checkNotNull(id);
        if (cache.contains(id)) {
            return true;
        }
        var found = read(id).isPresent();
        if (found) {
            cache(id);
        }
        return found;
    }

    protected abstract T create(TenantId id);

    private void cache(TenantId id) {
        cache.add(id);
    }

    @VisibleForTesting
    final boolean cached(TenantId id) {
        return cache.contains(id);
    }

    /**
     * Removes the passed value from the in-memory cache of known tenant IDs.
     *
     * <p>Implementations should call this method for removing the cached value
     * for a tenant for which the record was removed from the repository.
     *
     * @param id
     *         the ID to remove from the cache
     * @return {@code true} if the value was cached before and removed, {@code false} otherwise
     */
    protected final boolean unCache(TenantId id) {
        var result = cache.remove(id);
        return result;
    }

    /**
     * Clears the cache of known tenant IDs.
     */
    protected final void clearCache() {
        cache.clear();
    }

    @Override
    public final Set<TenantId> all() {
        var index = index();
        Set<TenantId> result = ImmutableSet.copyOf(index);
        cache.addAll(result);
        return result;
    }
}
