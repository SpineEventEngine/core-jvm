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

package io.spine.server.migration.mirror.given;

import io.spine.environment.Tests;
import io.spine.server.ServerEnvironment;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.migration.mirror.MirrorStorage;
import io.spine.system.server.Mirror;
import io.spine.testing.server.blackbox.BlackBox;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;

public final class MirrorMigrationTestEnv {

    private MirrorMigrationTestEnv() {
    }

    /**
     * Asserts that the migrated records have the expected columns, so they are queryable
     * from a Bounded Context.
     */
    public static void assertWithinBc(EntityRecordStorage<ParcelId, Parcel> entityRecordStorage,
                                      int expectedDelivered,
                                      int expectedInProgress) {

        ServerEnvironment.instance().reset();
        ServerEnvironment.when(Tests.class)
                         .use(PreparedStorageFactory.with(entityRecordStorage));

        var context = BlackBox.singleTenantWith(ParcelAgg.class);
        var client = context.clients()
                            .withMatchingTenant()
                            .asGuest();

        var delivered = client.run(
                Parcel.query()
                      .delivered().is(true)
                      .build()
        );
        var parcels = client.run(
                Parcel.query()
                      .delivered().is(false)
                      .build()
        );

        assertThat(delivered).hasSize(expectedDelivered);
        assertThat(parcels).hasSize(expectedInProgress);

        ServerEnvironment.instance().reset();
        context.close();
    }

    /**
     * Asserts that the given storage has the expected number
     * of active, archived and deleted records.
     */
    public static void assertEntityRecords(EntityRecordStorage<ParcelId, Parcel> storage,
                                           int active, int archived, int deleted) {

        var records = new FetchedEntityRecords<>(storage);

        var activeRecords = records.active();
        var archivedRecords = records.archived();
        var deletedRecords = records.deleted();

        assertThat(activeRecords).hasSize(active);
        assertThat(archivedRecords).hasSize(archived);
        assertThat(deletedRecords).hasSize(deleted);
    }

    /**
     * Asserts that the migrated {@code Mirror} projections have received
     * the updated {@link Mirror#getWasMigrated()} flag.
     */
    public static void assertMigratedMirrors(MirrorStorage mirrorStorage, int expected) {
        var migratedNumber = mirrorStorage.queryBuilder()
                                          .where(Mirror.Column.wasMigrated()).is(true)
                                          .build();
        var iterator = mirrorStorage.readAll(migratedNumber);
        var migratedMirrors = newArrayList(iterator);
        assertThat(migratedMirrors).hasSize(expected);
    }

    /**
     * Asserts that all batches during the migration were of the expected size.
     */
    public static void assertUsedBatchSize(MemoizingMonitor monitor, int batchSize) {
        monitor.completedSteps()
                  .forEach(step -> assertThat(step.getValue()).isAtMost(batchSize));
    }
}
