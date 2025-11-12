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

import io.spine.annotation.SPI;
import io.spine.system.server.Mirror;

/**
 * Directs and oversees the process of {@link MirrorMigration}.
 *
 * <p>There are three responsibility of a monitor:
 *
 * <ol>
 *     <li>Dictates a {@link #batchSize() batch size} which is a number of records
 *         processed within a single request.
 *     <li>When the processing of a current batch is completed, a monitor is
 *         {@link #shouldContinueAfter(MirrorsMigrated) asked}
 *         whether to continue or terminate.
 *     <li>Receives notifications about the key stages in the course of the migration.
 * </ol>
 *
 * <p>This class is open for extending. It may come in handy in the next cases:
 *
 * <ol>
 *     <li>When an application environment imposes restrictions on a session duration. Which means,
 *         the migration can't be completed in a single run. For example, one can
 *         {@link #shouldContinueAfter(MirrorsMigrated) terminate} the migration after the desired
 *         time since {@link #onMigrationStarted() start}. And then run it again to continue.
 *     <li>When there is a need to collect data about the course of the migration. One can measure
 *         speed of the whole process or for every batch. And be aware about the number
 *         of already migrated mirrors.
 * </ol>
 */
@SPI
public class MirrorMigrationMonitor {

    private final int batchSize;

    /**
     * Creates a monitor with the number of records, which should be processed
     * in a single request during the migration.
     */
    public MirrorMigrationMonitor(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Number of records, processed in a single request during the migration.
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * Determines if the ongoing migration should be continued
     * after the given batch of mirrors was processed.
     *
     * <p>When {@code false} is returned, the migration terminates its execution.
     *
     * <ol>
     *     <li>In order to <b>continue</b> the interrupted migration, just
     *         {@linkplain MirrorMigration#run(MirrorMigrationMonitor) run} it again. The migration
     *         process {@linkplain Mirror#getWasMigrated() marks} already migrated records. Thus,
     *         they will not be migrated twice and duplicated.
     *     <li>In order to <b>re-run</b> the migration, values of {@link Mirror#getWasMigrated()}
     *          column should be reset to {@code false} or no value. And this is the responsibility
     *          of a user.
     * </ol>
     *
     * <p>This method is not called when during the last request zero mirrors were migrated.
     * In this case the migration is considered completed anyway.
     *
     * @param migrated
     *         the last completed step
     */
    public boolean shouldContinueAfter(MirrorsMigrated migrated) {
        return true;
    }

    /**
     * Called when the migration is started.
     */
    public void onMigrationStarted() {
        // do nothing.
    }

    /**
     * Called when the migration is completed.
     *
     * <p>This method is called in two cases:
     *
     * <ol>
     *     <li>During the last batch, less than {@linkplain #batchSize() batch size} mirrors
     *         were migrated. It indicates that no records left to migrate.
     *     <li>The migration has been {@linkplain #shouldContinueAfter(MirrorsMigrated) terminated}
     *         ahead of time by a monitor.
     * </ol>
     */
    public void onMigrationCompleted() {
        // do nothing.
    }

    /**
     * Called when the next batch is started.
     */
    public void onBatchStarted() {
        // do nothing.
    }

    /**
     * Called when the current batch is completed.
     *
     * @param migrated
     *         number of mirrors, migrated within the last batch
     */
    public void onBatchCompleted(MirrorsMigrated migrated) {
        // do nothing.
    }
}
