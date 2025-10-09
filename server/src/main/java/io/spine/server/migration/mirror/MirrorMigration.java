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

package io.spine.server.migration.mirror;

import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.server.ContextSpec;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;
import io.spine.system.server.Mirror;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Migrates {@link Mirror} projections to {@code EntityRecord} with columns.
 *
 * <p>{@code Mirror} projection was deprecated in Spine 2.0. Previously, it was used to store
 * aggregates' states to allow their querying. A single projection stored states of <b>all</b>
 * aggregates in a Bounded Context.
 *
 * <p>Now, {@link EntityRecordStorage} is used to store the aggregate's states when it is
 * open for querying. This storage is created on a <b>per-aggregate</b> basis. This enables
 * storing of queryable state-based columns along the state itself. For this reason, the migration
 * is also done on a per-aggregate basis.
 *
 * <p>In Spine 2.0 a new optional field has been added to {@code Mirror}.
 * {@link Mirror#getWasMigrated()} tells whether the given mirror record was already migrated.
 * The migration relies on a value of this field. It migrates only those records, for which a value
 * of this field is not set at all or equals to {@code false}.
 *
 * <p>There is no particular ordering in which mirrors are fetched. For every batch
 * just the first {@code x} non-migrated records are queried. But, limiting the resulting dataset
 * requires at least one sorting directive. For this reason, the query result is additionally sorted
 * by {@link Mirror#getWasMigrated()}, which in fact makes no effect on the resulting dataset.
 *
 * <p>Mirrors, which are marked as archived or deleted – will be migrated as well.
 *
 * <p><b>An example usage</b>
 *
 * <pre>
 * // Create an instance of `MirrorMigration`.
 *
 * var context = ContextSpec.singleTenant("...");
 * var factory = ServerEnvironment.instance().storageFactory();
 * var aggToMigrate = ParcelAgg.class;
 * var migration = new MirrorMigration(context, factory, aggToMigrate);
 *
 * // Create an instance of `MirrorMigrationMonitor`. We are going to use
 * // a default implementation, which always continues the migration.
 * // Also, the mirrors will be migrated in batches of 500 records.
 *
 * var batchSize = 500;
 * var monitor = new MirrorMigrationMonitor(batchSize);
 *
 * // Run the migration.
 *
 * migration.run(monitor);
 * </pre>
 *
 * @param <I>
 *         the aggregate's identifier
 * @param <S>
 *         the aggregate's state
 * @param <A>
 *         the aggregate's class
 */
@Immutable
public final class MirrorMigration<I, S extends EntityState<I>, A extends Aggregate<I, S, ?>> {

    private final MirrorStorage mirrorStorage;
    private final EntityRecordStorage<I, S> entityRecordStorage;
    private final MirrorToEntityRecord<I, S, A> transformation;
    private final String aggregateType;

    /**
     * Creates a new instance of {@code MirrorMigration}.
     *
     * <p>The created instance is able to migrate {@code Mirror} projections of the
     * given aggregate class.
     *
     * @param context
     *         the context to which the passed aggregate belongs
     * @param factory
     *         the storage provider
     * @param aggClass
     *         the class of an aggregate, {@code Mirror} projections of which are to be migrated
     */
    public MirrorMigration(ContextSpec context,
                           StorageFactory factory,
                           Class<A> aggClass) {

        this.mirrorStorage = factory.createMirrorStorage(context);
        this.entityRecordStorage = factory.createEntityRecordStorage(context, aggClass);
        this.transformation = new MirrorToEntityRecord<>(aggClass);
        this.aggregateType = AggregateClass.asAggregateClass(aggClass)
                                           .stateTypeUrl()
                                           .value();
    }

    /**
     * Migrates {@link Mirror} projections to the aggregate's {@link EntityRecordStorage}.
     */
    public void run(MirrorMigrationMonitor monitor) {
        monitor.onMigrationStarted();

        var batchSize = monitor.batchSize();
        var migrated = proceed(monitor);
        while (migrated.getValue() == batchSize && monitor.shouldContinueAfter(migrated)) {
            migrated = proceed(monitor);
        }

        monitor.onMigrationCompleted();
    }

    /**
     * Fetches and processes the next batch.
     */
    private MirrorsMigrated proceed(MirrorMigrationMonitor monitor) {
        monitor.onBatchStarted();

        var batchSize = monitor.batchSize();
        var mirrors = fetchMirrors(batchSize);
        var batch = new MigrationBatch(batchSize);

        batch.transform(mirrors);

        entityRecordStorage.writeAll(batch.entityRecords());
        mirrorStorage.writeBatch(batch.migratedMirrors());

        var migrated = MirrorsMigrated.newBuilder()
                .setValue(batch.migratedMirrors()
                               .size())
                .build();

        monitor.onBatchCompleted(migrated);
        return migrated;
    }

    private Iterator<Mirror> fetchMirrors(int batchSize) {
        var query = mirrorStorage.queryBuilder()
                                 .where(Mirror.Column.aggregateType())
                                 .is(aggregateType)
                                 .where(Mirror.Column.wasMigrated())
                                 .is(false)
                                 .sortAscendingBy(Mirror.Column.wasMigrated())
                                 .limit(batchSize)
                                 .build();
        var iterator = mirrorStorage.readAll(query);
        return iterator;
    }

    /**
     * Returns a source {@link MirrorStorage}, from which mirrors are to be read and migrated.
     */
    @VisibleForTesting
    MirrorStorage sourceStorage() {
        return mirrorStorage;
    }

    /**
     * Returns a destination {@link EntityRecordStorage}, to which the transformed mirrors
     * will be written.
     */
    @VisibleForTesting
    EntityRecordStorage<I, S> destinationStorage() {
        return entityRecordStorage;
    }

    private class MigrationBatch {

        private final Collection<RecordWithColumns<I, EntityRecord>> entityRecords;
        private final Collection<Mirror> migratedMirrors;

        private MigrationBatch(int expectedSize) {
            entityRecords = new ArrayList<>(expectedSize);
            migratedMirrors = new ArrayList<>(expectedSize);
        }

        /**
         * Goes through the passed mirrors, transforms them into entity records
         * and accumulates the result.
         */
        private void transform(Iterator<Mirror> mirrors) {
            mirrors.forEachRemaining(mirror -> {
                var entityRecord = transformation.apply(mirror);
                var migratedMirror = mirror.toBuilder()
                        .setWasMigrated(true)
                        .build();

                entityRecords.add(entityRecord);
                migratedMirrors.add(migratedMirror);
            });
        }

        private Collection<RecordWithColumns<I, EntityRecord>> entityRecords() {
            return entityRecords;
        }

        private Collection<Mirror> migratedMirrors() {
            return migratedMirrors;
        }
    }
}
