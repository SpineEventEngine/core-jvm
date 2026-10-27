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

package io.spine.server.delivery.given;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.core.Versions;
import io.spine.protobuf.AnyPacker;
import io.spine.server.delivery.CatchUp;
import io.spine.server.delivery.CatchUpId;
import io.spine.server.delivery.CatchUpStatus;
import io.spine.server.delivery.CatchUpStorage;
import io.spine.server.entity.EntityRecord;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Provides the instances of {@link CatchUp} jobs to use as a data in tests.
 */
public final class TestCatchUpJobs {

    /**
     * Does not allow to instantiate this utility class.
     */
    private TestCatchUpJobs() {
    }

    /**
     * Creates a new {@code CatchUp} job according to the passed parameters.
     *
     * @param projectionType
     *         the type URL of the state of the catching-up projection
     * @param status
     *         the status of the catch-up
     * @param sinceWhen
     *         since when the catch-up is reading the messages
     * @param ids
     *         identifiers of the catching-up targets,
     *         or {@code null} if all instances of the projection type are catching-up
     * @return the state of the catch-up job
     */
    public static CatchUp catchUpJob(TypeUrl projectionType,
                                     CatchUpStatus status,
                                     Timestamp sinceWhen,
                                     @Nullable Collection<Object> ids) {
        var catchUpId = CatchUpId.newBuilder()
                .setUuid(Identifier.newUuid())
                .setProjectionType(projectionType.value())
                .build();
        var requestBuilder = CatchUp.Request.newBuilder().setSinceWhen(sinceWhen);
        if (ids != null) {
            for (var id : ids) {
                requestBuilder.addTarget(Identifier.pack(id));
            }
        }
        var request = requestBuilder.build();

        var result = CatchUp.newBuilder()
                .setId(catchUpId)
                .setStatus(status)
                .setRequest(request)
                .build();
        return result;
    }

    /**
     * Writes the passed job to the passed storage, as the record of a catch-up process.
     *
     * @param storage
     *         the storage to write the job to
     * @param job
     *         the job to write
     */
    public static void writeJob(CatchUpStorage storage, CatchUp job) {
        var id = job.getId();
        var record = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(id))
                .setState(AnyPacker.pack(job))
                .setVersion(Versions.zero())
                .build();
        storage.write(id, record);
    }
}
