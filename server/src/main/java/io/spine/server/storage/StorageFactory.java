/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.storage;

import com.google.protobuf.Message;
import io.spine.base.AggregateState;
import io.spine.base.EntityState;
import io.spine.server.Closeable;
import io.spine.server.ContextSpec;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateStorage;
import io.spine.server.delivery.CatchUpStorage;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.entity.Entity;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.EntityStateHistoryStorage;
import io.spine.server.entity.storage.HistorySpec;
import io.spine.server.event.EventStore;
import io.spine.server.event.store.DefaultEventStore;
import io.spine.server.migration.mirror.MirrorStorage;

/**
 * A factory for creating storages used by repositories, {@link EventStore EventStore}
 * and {@link io.spine.server.delivery.Delivery}.
 *
 * <p>The applications built with Spine use serialized Protobuf messages as a format of
 * storing the data objects. There is a number of storage types, each of them packs
 * their run-time information into a Proto message of a certain kind.
 *
 * <p>In order to unify the structure of the stored information and the way of its further
 * retrieval, a {@link RecordStorage} type is made a common ground for all other storage
 * types. It is capable of storing and querying any Protobuf messages and provides
 * a configuration API for detailing on how each message is transformed into a stored record.
 *
 * <p>To achieve that, each of the storage classes starts its own initialization by creating
 * an underlying {@code RecordStorage} and customizing it with a {@linkplain RecordSpec
 * specification of the Proto message} to store. Once that is done, all operations are
 * run through this delegate instance.
 *
 * <p>Such an approach brings another advantage for SPI users, too. It is sufficient to provide
 * just a new {@code RecordStorage} implementation in order to extend the storage factory
 * to support a certain DBMS. The rest of storage types just remain as delegating types,
 * and their code may be kept agnostic of low-level DBMS details. However, if one wants to extend
 * the functionality even further, any storage type may be extended and customized.
 *
 * <p>Another design intention is that all storage types that are presumed to work in a scope of
 * some Bounded Context — down to a {@code RecordStorage} — would take a {@linkplain ContextSpec
 * context specification} as the first parameter. An idea is that they may need to use
 * the properties of the Bounded Context (such as its name) in their low-level I/O with
 * the database. Only two of the storage types do not follow this concept: {@link InboxStorage}
 * and {@link CatchUpStorage}. The reason for that is that they are a part of
 * a {@link io.spine.server.delivery.Delivery} that is shared across all Bounded Contexts.
 * One more storage that stands apart from this idea is
 * a {@link io.spine.server.tenant.TenantStorage}. While it uses a {@code StorageFactory}
 * for an initialization, it is a part of a special {@code Tenants} context, which is also shared
 * between Bounded Contexts of an application.
 *
 * <p>See the package-level documentation of {@code io.spine.query} for more details on
 * record specification and querying.
 */
public interface StorageFactory extends Closeable {

    /**
     * Creates a new {@link RecordStorage}.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param recordSpec
     *         the specification of the record format in which the items are stored
     * @param <I>
     *         the type of the record identifiers
     * @param <R>
     *         the type of the stored records
     * @apiNote All other storage types delegate all their operations to
     *         a {@code RecordStorage} and therefore use this method during their initialization
     *         to create a private instance of a record storage.
     */
    <I, R extends Message> RecordStorage<I, R>
    createRecordStorage(ContextSpec context, RecordSpec<I, R> recordSpec);

    /**
     * Creates a new {@link RecordStorage} serving a per-entity history.
     *
     * <p>A history is allocated apart from the storages created via
     * {@link #createRecordStorage(ContextSpec, RecordSpec) createRecordStorage},
     * and apart from the other histories: the physical storage — a table, a kind —
     * is identified by the source type and the item type of the passed
     * specification, together; its name is at the discretion of the factory.
     * See {@link HistorySpec}.
     *
     * <p>The default implementation delegates to {@code createRecordStorage},
     * which only suits the factories isolating each created storage, such as
     * the in-memory one. A factory mapping equal record specifications to one
     * physical storage must override this method.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param spec
     *         the specification of the history
     * @param <I>
     *         the type of the record identifiers
     * @param <M>
     *         the type of the stored history items
     * @see io.spine.server.entity.storage.HistoryStorage
     */
    default <I, M extends Message> RecordStorage<I, M>
    createHistoryStorage(ContextSpec context, HistorySpec<I, M> spec) {
        return createRecordStorage(context, spec.getRecordSpec());
    }

    /**
     * Creates a new {@link AggregateStorage}.
     *
     * @param <I>
     *         the type of aggregate IDs
     * @param <S>
     *         the type of aggregate state
     * @param context
     *         specification of the Bounded Context, in scope of which the storage will be used
     * @param aggregateCls
     *         the class of {@code Aggregate}s to be stored
     */
    default <I, S extends AggregateState<I>> AggregateStorage<I, S>
    createAggregateStorage(ContextSpec context, Class<? extends Aggregate<I, S, ?>> aggregateCls) {
        return new AggregateStorage<>(context, aggregateCls, this);
    }

    /**
     * Creates a new {@link EntityEventStorage}.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param entityStateClass
     *         the class of the entity state; paired with the stored item type, it identifies
     *         the physical storage, keeping the event journals of different entity types
     *         apart (see {@link HistorySpec})
     */
    default EntityEventStorage
    createEntityEventStorage(ContextSpec context,
                             Class<? extends EntityState<?>> entityStateClass) {
        return new EntityEventStorage(context, this, entityStateClass);
    }

    /**
     * Creates a new {@link EventStore}.
     *
     * @param context
     *         specification of the Bounded Context events of which the store would serve
     */
    default EventStore createEventStore(ContextSpec context) {
        return new DefaultEventStore(context, this);
    }

    /**
     * Creates a new {@link EntityRecordStorage}.
     *
     * @param <I>
     *         the type of entity IDs
     * @param <S>
     *         the type of the entity state
     * @param context
     *         specification of the Bounded Context, in the scope of which this storage will be used
     * @param entityClass
     *         the class of entities to be stored
     */
    default <I, S extends EntityState<I>> EntityRecordStorage<I, S>
    createEntityRecordStorage(ContextSpec context, Class<? extends Entity<I, S>> entityClass) {
        var result = new EntityRecordStorage<>(context, this, entityClass);
        return result;
    }

    /**
     * Creates a new {@link EntityStateHistoryStorage}.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param entityStateClass
     *         the class of the entity state; paired with the stored item type, it identifies
     *         the physical storage, keeping the state histories of different entity types
     *         apart (see {@link HistorySpec})
     */
    default EntityStateHistoryStorage
    createEntityStateHistoryStorage(ContextSpec context,
                                    Class<? extends EntityState<?>> entityStateClass) {
        return new EntityStateHistoryStorage(context, this, entityStateClass);
    }

    /**
     * Creates a new {@link InboxStorage}.
     *
     * <p>The instance of {@code InboxStorage} is used in the {@link
     * io.spine.server.delivery.Delivery Delivery} operations. Therefore, there is typically just
     * a single instance of {@code InboxStorage} per {@link io.spine.server.ServerEnvironment
     * ServerEnvironment} instance, unlike other {@code Storage} types whose instances are created
     * per-{@link io.spine.server.BoundedContext BoundedContext}.
     *
     * @param multitenant
     *         whether the created storage should be multi-tenant
     */
    default InboxStorage createInboxStorage(boolean multitenant) {
        return new InboxStorage(this, multitenant);
    }

    /**
     * Creates a new {@link CatchUpStorage}.
     *
     * <p>Similar to {@link InboxStorage}, this type of storage is also used in the {@link
     * io.spine.server.delivery.Delivery Delivery} routines. So by default there is a single
     * instance of {@code CatchUpStorage} per {@link io.spine.server.ServerEnvironment
     * ServerEnvironment}.
     *
     * @param multitenant
     *         whether the created storage should be multi-tenant
     */
    default CatchUpStorage createCatchUpStorage(boolean multitenant) {
        return new CatchUpStorage(this, multitenant);
    }

    /**
     * Creates a new {@link MirrorStorage}.
     *
     * <p>Pay attention, {@link io.spine.system.server.Mirror Mirror} was deprecated in Spine 2.0.
     * The presence of this storage in the factory is for those who will
     * {@linkplain io.spine.server.migration.mirror.MirrorMigration migrate mirrors} from Spine 1.x.
     *
     * @param context
     *         specification of the Bounded Context, in the scope of which this storage will be used
     */
    default MirrorStorage createMirrorStorage(ContextSpec context) {
        return new MirrorStorage(context, this);
    }
}
