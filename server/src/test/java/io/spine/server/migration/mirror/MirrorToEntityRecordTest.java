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

import io.spine.server.entity.EntityRecord;
import io.spine.server.migration.mirror.given.ParcelAgg;
import io.spine.server.migration.mirror.given.ParcelId;
import io.spine.server.storage.RecordWithColumns;
import io.spine.system.server.Mirror;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static io.spine.server.migration.mirror.given.DeliveryService.generateCourier;
import static io.spine.server.migration.mirror.given.DeliveryService.generateInProgressParcel;
import static io.spine.server.migration.mirror.given.MirrorToEntityRecordTestEnv.assertEntityRecord;
import static io.spine.server.migration.mirror.given.MirrorToEntityRecordTestEnv.assertLifecycleColumns;
import static io.spine.server.migration.mirror.given.MirrorToEntityRecordTestEnv.assertStateColumns;
import static io.spine.server.migration.mirror.given.MirrorToEntityRecordTestEnv.mirror;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`MirrorToEntityRecord` should")
final class MirrorToEntityRecordTest {

    @Test
    @DisplayName("transform a `Mirror` into an `RecordWithColumns`")
    void mapMirrorToEntityRecord() {
        Function<Mirror, RecordWithColumns<ParcelId, EntityRecord>> transformation =
                new MirrorToEntityRecord<>(ParcelAgg.class);

        var parcel = generateInProgressParcel();
        var mirror = mirror(parcel);

        var recordWithColumns = transformation.apply(mirror);
        assertLifecycleColumns(recordWithColumns, mirror);
        assertStateColumns(recordWithColumns, mirror);

        var record = recordWithColumns.record();
        assertEntityRecord(record, mirror);
    }

    @Test
    @DisplayName("throw an exception when a mirror of an inappropriate entity class is passed")
    @SuppressWarnings("ResultOfMethodCallIgnored" /* the method throws an exception */)
    void throwException() {
        var transformation = new MirrorToEntityRecord<>(ParcelAgg.class);

        var courier = generateCourier();
        var mirror = mirror(courier);

        assertThrows(
                Exception.class,
                () -> transformation.apply(mirror)
        );
    }
}
