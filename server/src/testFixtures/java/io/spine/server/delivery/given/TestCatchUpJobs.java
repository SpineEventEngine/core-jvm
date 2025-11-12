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

package io.spine.server.delivery.given;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.server.delivery.CatchUp;
import io.spine.server.delivery.CatchUpId;
import io.spine.server.delivery.CatchUpStatus;
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
}
