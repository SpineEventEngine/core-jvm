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

package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.logging.WithLogging;
import io.spine.server.tenant.IdInTenant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * The cache of {@code Entity} objects for a certain {@code Repository} and
 * {@linkplain #startCaching(Object) selected} identifiers.
 *
 * <p>Reduces the number of both read and write storage operations in cases if more than one
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
 * @param <I>
 *         the type of {@code Entity} identifiers
 * @param <E>
 *         the type of entity
 */
@Internal
public final class RepositoryCache<I, E extends Entity<I, ?>> implements WithLogging {

    private final Map<IdInTenant<I>, E> cache = new HashMap<>();
    private final Set<IdInTenant<I>> idsToCache = new HashSet<>();

    private final boolean multitenant;
    private final Load<I, E> loadFn;
    private final Store<E> storeFn;

    /**
     * Creates the instance of the cache considering the multi-tenancy setting,
     * the function to load entities and the function to store the entity .
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
    public synchronized E load(I id) {
        var idInTenant = idInTenant(id);
        if (!idsToCache.contains(idInTenant)) {
            return loadFn.apply(idInTenant.value());
        }

        if (!cache.containsKey(idInTenant)) {
            var entity = loadFn.apply(idInTenant.value());
            cache.put(idInTenant, entity);
            return entity;
        }
        return cache.get(idInTenant);
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
    public synchronized void startCaching(I id) {
        idsToCache.add(idInTenant(id));
    }

    /**
     * Stops caching the {@code load} and {@code store} operations for the
     * {@code Entity} with the passed identifier.
     *
     * <p>Stores the cached entity to the entity repository via
     * {@linkplain RepositoryCache#RepositoryCache(boolean, Load, Store) pre-configured}
     * {@code Store} function.
     *
     * @param id
     *         an identifier of the entity to cache
     */
    public synchronized void stopCaching(I id) {
        var idInTenant = idInTenant(id);
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
        cache.remove(idInTenant);
        idsToCache.remove(idInTenant);
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
    public synchronized void store(E entity) {
        var id = entity.id();
        var idInTenant = idInTenant(id);
        if (idsToCache.contains(idInTenant)) {
            cache.put(idInTenant, entity);
        } else {
            storeFn.accept(entity);
        }
    }

    /**
     * A function which loads an {@code Entity} by ID from its real repository.
     *
     * @param <I>
     *         the type of {@code Entity} identifiers
     * @param <E>
     *         the type of entity
     */
    @FunctionalInterface
    public interface Load<I, E extends Entity<I, ?>> extends Function<I, E> {}

    /**
     * A function which stores the {@code Entity} to its real repository.
     *
     * @param <E>
     *         the type of entity
     */
    @FunctionalInterface
    public interface Store<E extends Entity<?, ?>> extends Consumer<E> {}
}
