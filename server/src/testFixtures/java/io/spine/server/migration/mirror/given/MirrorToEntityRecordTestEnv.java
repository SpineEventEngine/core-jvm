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

import com.google.protobuf.Any;
import io.spine.base.EntityState;
import io.spine.base.Time;
import io.spine.core.Version;
import io.spine.protobuf.AnyPacker;
import io.spine.protobuf.TypeConverter;
import io.spine.query.ColumnName;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.storage.RecordWithColumns;
import io.spine.system.server.Mirror;
import io.spine.system.server.MirrorId;

import static com.google.common.truth.Truth.assertThat;

public final class MirrorToEntityRecordTestEnv {

    private MirrorToEntityRecordTestEnv() {
    }

    /**
     * Asserts that the common fields in {@link EntityRecord} and {@link Mirror} match.
     */
    public static void assertEntityRecord(EntityRecord record, Mirror mirror) {
        assertThat(record.getEntityId()).isEqualTo(mirror.getId().getValue());
        assertThat(record.getState()).isEqualTo(mirror.getState());
        assertThat(record.getVersion()).isEqualTo(mirror.getVersion());
        assertThat(record.lifecycleFlags()).isEqualTo(mirror.getLifecycle());
    }

    /**
     * Asserts that version and lifecycle column values match to the ones from the mirror.
     */
    public static void assertLifecycleColumns(RecordWithColumns<?, EntityRecord> recordWithColumns,
                                              Mirror mirror) {

        var lifecycleFlags = mirror.getLifecycle();
        assertThat(recordWithColumns.columnValue(ColumnName.of("archived")))
                .isEqualTo(lifecycleFlags.getArchived());
        assertThat(recordWithColumns.columnValue(ColumnName.of("deleted")))
                .isEqualTo(lifecycleFlags.getDeleted());

        var version = mirror.getVersion();
        assertThat(recordWithColumns.columnValue(ColumnName.of("version")))
                .isEqualTo(version);
    }

    /**
     * Asserts that state-based columns defined for {@link Parcel} aggregate are present
     * in the record.
     *
     * <p>Those columns are specific to a concrete aggregate's state.
     */
    public static void assertStateColumns(RecordWithColumns<ParcelId, EntityRecord> recordWithColumns,
                                          Mirror mirror) {

        var parcel = AnyPacker.unpack(mirror.getState(), Parcel.class);
        assertThat(recordWithColumns.columnValue(ColumnName.of("recipient")))
                .isEqualTo(parcel.getRecipient());
        assertThat(recordWithColumns.columnValue(ColumnName.of("delivered")))
                .isEqualTo(parcel.getDelivered());
    }

    /**
     * Creates {@link Mirror} projection with the passed state and lifecycle.
     */
    public static Mirror mirror(EntityState<?> state, LifecycleFlags lifecycle) {
        var version = version();
        var mirrorId = mirrorId(state);
        var mirror = Mirror.newBuilder()
                .setId(mirrorId)
                .setState(Any.pack(state, state.typeUrl().prefix()))
                .setLifecycle(lifecycle)
                .setVersion(version)
                .setAggregateType(state.typeUrl().value())
                .build();
        return mirror;
    }

    /**
     * Creates {@link Mirror} projection with the passed state.
     */
    public static Mirror mirror(EntityState<?> state) {
        var lifecycle = lifecycle(false, false);
        var mirror = mirror(state, lifecycle);
        return mirror;
    }

    private static MirrorId mirrorId(EntityState<?> state) {
        return MirrorId.newBuilder()
                .setValue(id(state))
                .setTypeUrl(state.typeUrl().value())
                .build();
    }

    private static Any id(EntityState<?> state) {
        var idField = state.getDescriptorForType().findFieldByNumber(1);
        var stateId = state.getField(idField);
        return TypeConverter.toAny(stateId);
    }

    public static LifecycleFlags lifecycle(boolean archived, boolean deleted) {
        return LifecycleFlags.newBuilder()
                .setArchived(archived)
                .setDeleted(deleted)
                .build();
    }

    private static Version version() {
        return Version.newBuilder()
                .setNumber(12)
                .setTimestamp(Time.currentTime())
                .build();
    }
}
