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

package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.logging.WithLogging;
import io.spine.server.tenant.IdInTenant;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * The cache of {@code Entity} objects for a certain {@code Repository} and
 * {@linkplain #startCaching(Object) selected} identifiers.
 *
 * <p>Reduces the number of both read and write storage operations in cases when more than one
 * message is dispatched to the target entity. The typical scenario looks like this:
 *
 * <ol>
 *     <li>Several messages are being dispatched to the entity. The framework calls {@link
 *     #startCaching(Object) startCaching(entityId)} method.
 *
 *     <li>Before dispatching the first message, the repository loads the entity via the cache;
 *     the cache executes the read operation, remembers the result and returns the entity.
 *
 *     <li>The first message is dispatched to the entity. {@link #store(Entity) store(Entity)}
 *     method is called. Instead of executing the write operation right away, the cache stores
 *     the updated entity in its memory.
 *
 *     <li>More messages are dispatched to the same entity. Each read operation is served by the
 *     cache instead of executing the reads from the underlying storage. Upon entity updates,
 *     the changed entity is stored into the cache memory.
 *
 *     <li>The dispatching of the message batch is completed. The framework calls {@link
 *     #stopCaching(Object) stopCaching(entityId)} method. Then the cache pushes the updated entity
 *     to the underlying storage by executing the write operation.
 * </ol>
 *
 * <p>The users of this class should keep the number of the simultaneously cached entities
 * reasonable due to a potentially huge significant memory footprint.
 *
 * <p>The operations on the same entity — that is, on the same identifier within the same
 * tenant — are mutually exclusive. The operations on different entities do not wait for
 * each other, even while the function loading or storing an entity is running.
 *
 * @param <I>
 *         the type of {@code Entity} identifiers
 * @param <E>
 *         the type of entity
 */
@Internal
public final class RepositoryCache<I, E extends Entity<I, ?>> implements WithLogging {

    private final Map<IdInTenant<I>, E> cache = new ConcurrentHashMap<>();
    private final Set<IdInTenant<I>> idsToCache = ConcurrentHashMap.newKeySet();

    /**
     * The locks that make the operations on the same entity mutually exclusive.
     */
    private final KeyLocks<IdInTenant<I>> locks = new KeyLocks<>();

    private final boolean multitenant;
    private final Load<I, E> loadFn;
    private final Store<E> storeFn;

    /**
     * Creates the instance of the cache considering the multi-tenancy setting,
     * the function to load entities and the function to store the entity.
     */
    public RepositoryCache(boolean multitenant, Load<I, E> loadFn, Store<E> storeFn) {
        this.multitenant = multitenant;
        this.loadFn = loadFn;
        this.storeFn = storeFn;
    }

    /**
     * Loads the entity by its identifier.
     *
     * <p>If the target entity was previously {@linkplain #startCaching(Object) asked to be cached},
     * the entity is additionally stored into the cache internal memory.
     *
     * @param id
     *         the identifier of the entity to load
     * @return loaded entity
     */
    public E load(I id) {
        var idInTenant = idInTenant(id);
        return locks.evaluate(idInTenant, () -> {
            if (!idsToCache.contains(idInTenant)) {
                return loadFn.apply(idInTenant.value());
            }
            var cached = cache.get(idInTenant);
            if (cached != null) {
                return cached;
            }
            // Do not turn this into `computeIfAbsent()`. The map would then run the load
            // function itself: holding up other entities for as long as the function runs,
            // and possibly failing if the function comes back to this cache.
            var entity = loadFn.apply(idInTenant.value());
            cache.put(idInTenant, entity);
            return entity;
        });
    }

    /**
     * Starts caching the {@code load} and {@code store} operation results in memory
     * for the given {@code Entity} identifier.
     *
     * <p>Call {@linkplain #stopCaching(Object) stopCaching(entityId)} to flush the accumulated
     * entity update to the underlying storage via the pre-configured store function.
     *
     * @param id
     *         an identifier of the entity to cache
     */
    public void startCaching(I id) {
        var idInTenant = idInTenant(id);
        locks.run(idInTenant, () -> idsToCache.add(idInTenant));
    }

    /**
     * Stops caching the {@code load} and {@code store} operations for the
     * {@code Entity} with the passed identifier.
     *
     * <p>Stores the cached entity to the entity repository via
     * {@linkplain RepositoryCache#RepositoryCache(boolean, Load, Store) pre-configured}
     * {@code Store} function.
     *
     * <p>The caching stops even if the entity was never loaded, or if storing it fails. In the
     * latter case, the failure is propagated to the caller.
     *
     * @param id
     *         an identifier of the entity to cache
     */
    public void stopCaching(I id) {
        var idInTenant = idInTenant(id);
        locks.run(idInTenant, () -> {
            try {
                flush(idInTenant);
            } finally {
                cache.remove(idInTenant);
                idsToCache.remove(idInTenant);
            }
        });
    }

    /**
     * Stores the cached entity with the passed identifier if the entity was loaded.
     */
    private void flush(IdInTenant<I> idInTenant) {
        var entity = cache.get(idInTenant);
        if (entity == null) {
            logger().atWarning().log(() -> format(
                    "Cannot find the cached entity in the cache for ID `%s`." +
                            " Cache keys: `%s`. IDs to cache: `%s`." +
                            " Most likely, the entity was dispatched with messages" +
                            " but was never loaded by its repository.",
                    idInTenant, cache.keySet(), idsToCache));
            return;
        }
        storeFn.accept(entity);
    }

    private IdInTenant<I> idInTenant(I id) {
        return IdInTenant.of(id, multitenant);
    }

    /**
     * Stores the entity.
     *
     * <p>If the caching of this entity was previously {@linkplain #startCaching(Object) started},
     * the entity is cached in the memory only. Otherwise, a supplied direct store operation is
     * executed.
     *
     * <p>If the entity is being cached, the changes will travel from the in-memory cache to the
     * underlying storage when {@linkplain #stopCaching(Object) stopCaching(entityId)} method is
     * called.
     *
     * @param entity
     *         the entity to store
     */
    public void store(E entity) {
        var id = entity.id();
        var idInTenant = idInTenant(id);
        locks.run(idInTenant, () -> {
            if (idsToCache.contains(idInTenant)) {
                cache.put(idInTenant, entity);
            } else {
                storeFn.accept(entity);
            }
        });
    }

    /**
     * A function that loads an {@code Entity} by ID from its real repository.
     *
     * @param <I>
     *         the type of {@code Entity} identifiers
     * @param <E>
     *         the type of entity
     */
    @FunctionalInterface
    public interface Load<I, E extends Entity<I, ?>> extends Function<I, E> {}

    /**
     * A function that stores the {@code Entity} to its real repository.
     *
     * @param <E>
     *         the type of entity
     */
    @FunctionalInterface
    public interface Store<E extends Entity<?, ?>> extends Consumer<E> {}
}
