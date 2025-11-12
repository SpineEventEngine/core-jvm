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

import com.google.common.collect.Iterators;
import io.spine.server.ContextSpec;
import io.spine.server.migration.mirror.given.DeliveryService;
import io.spine.server.migration.mirror.given.MemoizingMonitor;
import io.spine.server.migration.mirror.given.MirrorToEntityRecordTestEnv;
import io.spine.server.migration.mirror.given.ParcelAgg;
import io.spine.server.migration.mirror.given.PreparedMirrorStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.migration.mirror.given.MirrorMigrationTestEnv.assertEntityRecords;
import static io.spine.server.migration.mirror.given.MirrorMigrationTestEnv.assertMigratedMirrors;
import static io.spine.server.migration.mirror.given.MirrorMigrationTestEnv.assertUsedBatchSize;
import static io.spine.server.migration.mirror.given.MirrorMigrationTestEnv.assertWithinBc;

@DisplayName("`MirrorMigration` should")
final class MirrorMigrationTest {

    private static final StorageFactory factory = InMemoryStorageFactory.newInstance();
    private static final String tenantId = MirrorToEntityRecordTestEnv.class.getSimpleName();
    private static final ContextSpec context = ContextSpec.singleTenant(tenantId);

    @Nested
    @DisplayName("migrate mirrors")
    class MigrateMirrors {

        @Nested
        @DisplayName("in batches of")
        class InBatchesOf {

            @Test
            @DisplayName("one hundred records")
            void oneHundredRecords() {
                testInBatchesOf(100);
            }

            @Test
            @DisplayName("one thousand records")
            void oneThousandRecords() {
                testInBatchesOf(1_000);
            }

            private void testInBatchesOf(int batchSize) {
                var delivered = 2_175;
                var inProgress = 3_780;

                var migration = new MirrorMigration<>(context, factory, ParcelAgg.class);
                var mirrorStorage = new PreparedMirrorStorage(migration.sourceStorage())
                        .put(DeliveryService::generateCourier, 3_000)
                        .put(DeliveryService::generateVehicle, 4_000)
                        .put(DeliveryService::generateDeliveredParcel, delivered)
                        .put(DeliveryService::generateInProgressParcel, inProgress)
                        .get();
                var monitor = new MemoizingMonitor(batchSize);

                migration.run(monitor);

                assertWithinBc(migration.destinationStorage(), delivered, inProgress);
                assertMigratedMirrors(mirrorStorage, delivered + inProgress);
                assertUsedBatchSize(monitor, batchSize);
            }
        }

        @Test
        @DisplayName("with deleted or archived flags")
        void withDeletedOrArchivedFlags() {
            var active = 2_175;
            var archived = 2_200;
            var deleted = 3_350;

            var migration = new MirrorMigration<>(context, factory, ParcelAgg.class);
            var mirrorStorage = new PreparedMirrorStorage(migration.sourceStorage())
                    .put(DeliveryService::generateDeliveredParcel, active)
                    .putDeleted(DeliveryService::generateDeliveredParcel, deleted)
                    .putArchived(DeliveryService::generateDeliveredParcel, archived)
                    .get();

            var batchSize = 500;
            var monitor = new MemoizingMonitor(batchSize);

            migration.run(monitor);

            assertEntityRecords(migration.destinationStorage(), active, archived, deleted);
            assertMigratedMirrors(mirrorStorage, active + archived + deleted);
            assertUsedBatchSize(monitor, batchSize);
        }
    }

    @Nested
    @DisplayName("notify a monitor")
    class NotifyMonitor {

        @Test
        @DisplayName("on a migration start and completion")
        void onMigrationRunning() {
            var monitor = new MemoizingMonitor(1_000);
            runMigration(monitor);

            assertThat(monitor.startedTimes()).isEqualTo(1);
            assertThat(monitor.completedTimes()).isEqualTo(1);
        }

        @Test
        @DisplayName("on a migration's batch start and completion")
        void onStepRunning() {
            var monitor = new MemoizingMonitor(1_000);
            runMigration(monitor);

            assertThat(monitor.stepStartedTimes()).isEqualTo(4);
            assertThat(monitor.completedSteps()).hasSize(4);
        }

        private void runMigration(MemoizingMonitor monitor) {
            var migration = new MirrorMigration<>(context, factory, ParcelAgg.class);
            new PreparedMirrorStorage(migration.sourceStorage())
                    .put(DeliveryService::generateInProgressParcel, 3_050);
            migration.run(monitor);
        }
    }

    @Test
    @DisplayName("terminate on a monitor's refusal")
    void terminateMigration() {
        var expectedNumber = 5_000;
        var migration = new MirrorMigration<>(context, factory, ParcelAgg.class);
        new PreparedMirrorStorage(migration.sourceStorage())
                .put(DeliveryService::generateInProgressParcel, expectedNumber);

        /* Create a migration monitor which fails after four steps. */
        var batchSize = 50;
        var monitor = new MirrorMigrationMonitor(batchSize) {
            private int steps;

            @Override
            public boolean shouldContinueAfter(MirrorsMigrated step) {
                ++steps;
                return steps <= 5;
            }
        };

        migration.run(monitor);

        @SuppressWarnings("resource") // Closed elsewhere.
        var destinationStorage = migration.destinationStorage();
        var migratedRecords =  Iterators.size(destinationStorage.index());

        assertThat(migratedRecords).isLessThan(expectedNumber);
        assertThat(migratedRecords).isAtLeast(batchSize);
    }
}
