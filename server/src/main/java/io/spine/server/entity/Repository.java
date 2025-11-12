/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.entity;

import com.google.common.collect.Iterators;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.annotation.Internal;
import io.spine.annotation.SPI;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Identifier;
import io.spine.base.Routable;
import io.spine.core.SignalContext;
import io.spine.logging.WithLogging;
import io.spine.reflect.GenericTypeIndex;
import io.spine.server.BoundedContext;
import io.spine.server.Closeable;
import io.spine.server.ContextAware;
import io.spine.server.ServerEnvironment;
import io.spine.server.entity.model.EntityClass;
import io.spine.server.route.RouteFn;
import io.spine.server.storage.Storage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.type.SignalEnvelope;
import io.spine.system.server.RoutingFailed;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.dataflow.qual.Pure;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.collect.Iterables.getFirst;
import static io.spine.base.Errors.fromThrowable;
import static io.spine.server.entity.model.EntityClass.asEntityClass;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Abstract base class for repositories.
 *
 * @param <I>
 *         the type of entity identifiers
 * @param <E>
 *         the type of managed entities
 */
@SuppressWarnings("ClassWithTooManyMethods") // OK for this core class.
public abstract class Repository<I, E extends Entity<I, ?>>
        implements ContextAware, Closeable, WithLogging {

    private static final String ERR_MSG_STORAGE_NOT_ASSIGNED = "Storage is not assigned.";

    /**
     * The {@link BoundedContext} to which the repository belongs.
     *
     * <p>This field is {@code null} when a repository is not
     * {@linkplain #registerWith(BoundedContext) registered with the context} yet, and
     * after the repository is already {@linkplain #close() closed}.
     */
    private @Nullable BoundedContext context;

    /**
     * Model class of entities managed by this repository.
     *
     * <p>This field is null if {@link #entityModelClass()} is never called.
     */
    private volatile @MonotonicNonNull EntityClass<E> entityClass;

    /**
     * The data storage for this repository.
     *
     * <p>This field is null if the storage was not initialized, or
     * the repository was {@linkplain #close() closed}.
     */
    private @Nullable Storage<I, ?> storage;

    /**
     * Creates the repository.
     */
    protected Repository() {
    }

    /**
     * Create a new entity instance with its default state.
     *
     * @param id the id of the entity
     * @return new entity instance
     */
    public abstract E create(I id);

    /**
     * Stores the passed object.
     *
     * <p>Note: The storage must be assigned before calling this method.
     *
     * @param obj an instance to store
     */
    protected abstract void store(E obj);

    /**
     * Finds an entity with the passed ID.
     *
     * @param id the ID of the entity to load
     * @return the entity or {@link Optional#empty()} if there's no entity with such ID
     */
    public abstract Optional<E> find(I id);

    /**
     * Returns an iterator over the entities managed by the repository that match the passed filter.
     *
     * <p>The returned iterator does not support removal.
     *
     * <p>Iteration through entities is performed by {@linkplain #find(Object) loading}
     * them one by one.
     */
    public Iterator<E> iterator(Predicate<E> filter) {
        Iterator<E> unfiltered = new EntityIterator<>(this);
        Iterator<E> filtered = Iterators.filter(unfiltered, filter::test);
        return filtered;
    }

    /**
     * Returns an iterator over the identifiers of all the entities managed by this repository.
     */
    public Iterator<I> index() {
        return storage().index();
    }

    /**
     * Obtains model class for the entities managed by this repository.
     */
    @Internal
    public EntityClass<E> entityModelClass() {
        if (entityClass == null) {
            @SuppressWarnings("unchecked") // The type is ensured by the declaration of this class.
            var cast = (Class<E>) GenericParameter.ENTITY.argumentIn(getClass());
            entityClass = toModelClass(cast);
        }
        return entityClass;
    }

    /**
     * Obtains a model class for the passed entity class value.
     */
    @Internal
    protected EntityClass<E> toModelClass(Class<E> cls) {
        return asEntityClass(cls);
    }

    /** Returns the class of IDs used by this repository. */
    @SuppressWarnings("unchecked") // The cast is ensured by generic parameters of the repository.
    public final Class<I> idClass() {
        return (Class<I>) entityModelClass().idClass();
    }

    /** Returns the class of entities managed by this repository. */
    public final Class<E> entityClass() {
        return entityModelClass().rawClass();
    }

    /**
     * Obtains the {@link TypeUrl} for the state objects wrapped by entities
     * managed by this repository.
     */
    public final TypeUrl entityStateType() {
        return entityModelClass().stateTypeUrl();
    }

    /**
     * Assigns a {@code BoundedContext} to this repository.
     *
     * <p>A context for a repository can be set only once. Passing the same second time will have
     * no effect.
     *
     * <p>If the repository is not {@linkplain #isOpen() opened} prior to this call, it is opened.
     *
     * @throws IllegalStateException
     *          if the repository has a context value already assigned, and the passed value is
     *          not equal to the assigned one
     */
    @OverridingMethodsMustInvokeSuper
    @Override
    @Internal
    public void registerWith(BoundedContext context) {
        checkNotNull(context);
        var sameValue = context.equals(this.context);
        if (hasContext() && !sameValue) {
            throw newIllegalStateException(
                    "The repository `%s` has the Bounded Context (`%s`) assigned." +
                            " This operation can be performed only once." +
                            " Attempted to set: `%s`.",
                    this, this.context, context);
        }
        if (sameValue) {
            return;
        }
        this.context = context;
        open();
        if (isTypeSupplier()) {
            context.stand()
                   .registerTypeSupplier(this);
        }
    }

    /**
     * Tells if the repository is registered in a {@code BoundedContext}.
     */
    @Override
    public boolean isRegistered() {
        return hasContext();
    }

    /**
     * Tells if this repository should be registered as a type supplier with a {@code Stand}
     * of the {@code BoundedContext} to which this repository belongs.
     *
     * <p>Normally repositories are type suppliers. Some types of internal repositories are
     * not type suppliers because data of their entities should not be exposed.
     * Those classes of repositories should overwrite this method returning {@code false}.
     *
     * @return true by default
     */
    @Internal
    protected boolean isTypeSupplier() {
        return true;
    }

    /**
     * Verifies whether the repository is registered with a {@code BoundedContext}.
     */
    protected final boolean hasContext() {
        return context != null;
    }

    /**
     * Obtains the {@code BoundedContext} to which this repository belongs.
     *
     * @return parent {@code BoundedContext}
     * @throws IllegalStateException
     *         if the repository has no context assigned
     */
    @SuppressWarnings("DataFlowIssue") // non-null result ensured by `hasContext()` call.
    protected final BoundedContext context() {
        checkState(hasContext(),
                   "The repository (class: `%s`) is not registered with a `BoundedContext`.",
                   getClass().getName());
        return context;
    }

    /**
     * The callback is invoked by a {@link BoundedContext} when adding the repository.
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // see Javadoc
    @OverridingMethodsMustInvokeSuper
    public void onRegistered() {
        // Do nothing by default.
    }

    /**
     * Initializes the storage of the repository.
     */
    protected final void open() {
        var storage = storage();
        checkNotNull(storage, "Unable to initialize the storage.");
    }

    /**
     * Obtains {@code StorageFactory} associated with the {@code ServerEnvironment} for
     * {@linkplain #createStorage() creating} standard storages.
     *
     * <p>In order to create a custom storage, please override {@link #createStorage()} providing
     * custom implementation.
     *
     * @see #createStorage()
     */
    @Internal
    protected static StorageFactory defaultStorageFactory() {
        return ServerEnvironment.instance().storageFactory();
    }

    /**
     * Returns the storage assigned to this repository.
     *
     * <p>To verify if the storage is assigned, use {@link #storageAssigned()}.
     *
     * @throws IllegalStateException if the storage is not assigned
     */
    protected final Storage<I, ?> storage() {
        if (storage == null) {
            this.storage = createStorage();
        }
        return checkStorage(storage);
    }

    /**
     * Returns {@code true} if the storage is assigned, {@code false} otherwise.
     */
    @VisibleForTesting
    public final boolean storageAssigned() {
        return storage != null;
    }

    /**
     * Ensures that the storage is not null.
     *
     * @return passed value if it is not null
     * @throws IllegalStateException if the passed instance is null
     */
    protected static <S extends Closeable> @NonNull S checkStorage(@Nullable S storage) {
        checkState(storage != null, ERR_MSG_STORAGE_NOT_ASSIGNED);
        return storage;
    }

    /**
     * Creates the storage for this repository.
     *
     * <p>Default implementations use {@link #defaultStorageFactory()} invoking its method
     * which creates a storage compatible with the repository.
     *
     * <p>Overwrite this method for creating a custom implementation of {@code Storage}.
     *
     * @return the created storage instance
     */
    protected abstract Storage<I, ?> createStorage();

    /**
     * Closes the repository by closing the underlying storage.
     *
     * <p>The reference to the storage becomes null after this call.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void close() {
        if (isOpen()) {
            storage().close();
            this.storage = null;
        }
        this.context = null;
    }

    /**
     * Verifies if the repository is open.
     */
    @Override
    public final boolean isOpen() {
        return hasContext();
    }

    @Internal
    protected final <M extends Routable, C extends SignalContext, R> Optional<R>
    route(RouteFn<M, C, R> routing, SignalEnvelope<?, ?, C> envelope) {
        try {
            @SuppressWarnings("unchecked")
            var message = (M) envelope.message();
            var result = routing.invoke(message, envelope.context());
            checkMatchesIdType(result);
            return Optional.of(result);
        } catch (RuntimeException e) {
            var cause = getRootCause(e);
            onRoutingFailed(envelope, cause);
            return Optional.empty();
        }
    }

    /**
     * Ensures that the passed routing result corresponds to the {@linkplain #idClass() type of
     * the identifiers} served by this repository.
     *
     * <p>An {@code IllegalStateException} is thrown otherwise.
     *
     * @param result the result of routing
     */
    private void checkMatchesIdType(Object result) {
        Class<?> routingResultType = null;
        if (result instanceof Iterable) {
            var asIterable = (Iterable<?>) result;
            var element = getFirst(asIterable, null);
            if (element != null) {
                routingResultType = element.getClass();
            }
        } else {
            routingResultType = result.getClass();
        }
        if (routingResultType != null) {
            var idClass = idClass();
            var corresponds = idClass.isAssignableFrom(routingResultType);
            checkArgument(corresponds, "Message routing failed in `%s` due to the type mismatch. " +
                                  "Expected ID type is `%s`, but was `%s`.",
                          getClass().getName(), idClass.getName(), routingResultType.getName());
        }
    }

    /**
     * A callback invoked when an exception is thrown from message routing.
     *
     * @param envelope the routed signal
     * @param cause the root cause of the exception
     */
    @OverridingMethodsMustInvokeSuper
    @Internal
    protected void onRoutingFailed(SignalEnvelope<?, ?, ?> envelope, Throwable cause) {
        var systemEvent = RoutingFailed.newBuilder()
                .setEntityType(entityModelClass().typeName())
                .setHandledSignal(envelope.messageId())
                .setError(fromThrowable(cause))
                .build();
        context().systemClient()
                 .writeSide()
                 .postEvent(systemEvent, envelope.asMessageOrigin());
    }

    /**
     * Obtains an instance of {@link EntityLifecycle} for the entity with the given ID.
     *
     * <p>It is necessary that a tenant ID is set when calling this method in a multitenant
     * environment.
     *
     * @param id the ID of the target entity
     * @return {@link EntityLifecycle} of the given entity
     */
    @Internal
    public EntityLifecycle lifecycleOf(I id) {
        checkNotNull(id);
        var writeSide = context().systemClient()
                                 .writeSide();
        var eventFilter = eventFilter();
        var lifecycle = EntityLifecycle.newBuilder()
                .setEntityId(id)
                .setEntityType(entityModelClass())
                .setSystemWriteSide(writeSide)
                .setEventFilter(eventFilter)
                .build();
        return lifecycle;
    }

    /**
     * Creates an {@link EventFilter} for this repository.
     *
     * <p>All the events posted by this repository, domain, and system are first passed through this
     * filter.
     *
     * <p>By default, the filter allows all the events to be posted. For entities which do not allow
     * state subscription, the {@link io.spine.system.server.event.EntityStateChanged} event is
     * filtered out. Override this method to change this behaviour.
     *
     * @return an {@link EventFilter} to apply to all posted events
     * @implNote This method may be called many times for a single repository. It is reasonable that
     *           it does not re-initialize the filter each time. Also, it is necessary that
     *           the filter returned from this method is always (at least effectively) the same.
     *           See {@link Pure @Pure} for the details on the expected behaviour.
     */
    @SPI
    @Pure
    public EventFilter eventFilter() {
        var entityClass = entityModelClass();
        return EntityStateChangedFilter.forType(entityClass);
    }

    /**
     * Enumeration of generic type parameters of this class.
     */
    @SuppressWarnings("rawtypes")
    private enum GenericParameter implements GenericTypeIndex<Repository> {

        /** The index of the generic type {@code <I>}. */
        ID(0),

        /** The index of the generic type {@code <E>}. */
        ENTITY(1);

        private final int index;

        GenericParameter(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return this.index;
        }
    }

    /**
     * An iterator of all entities from the storage.
     *
     * <p>This iterator does not allow removal.
     *
     * @param <I>
     *         the type of entity identifiers
     * @param <E>
     *         the type of entities
     */
    private static class EntityIterator<I, E extends Entity<I, ?>> implements Iterator<E> {

        private final Repository<I, E> repository;
        private final Iterator<I> index;

        private EntityIterator(Repository<I, E> repository) {
            this.repository = repository;
            this.index = repository.storage()
                                   .index();
        }

        @Override
        public boolean hasNext() {
            var result = index.hasNext();
            return result;
        }

        @Override
        public E next() {
            var id = index.next();
            var loaded = repository.find(id);
            if (loaded.isEmpty()) {
                var idStr = Identifier.toString(id);
                throw newIllegalStateException("Unable to load entity with ID: `%s`.", idStr);
            }

            var entity = loaded.get();
            return entity;
        }
    }
}
