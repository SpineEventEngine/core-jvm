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

package io.spine.testing.server.query;

import io.spine.client.EntityStateWithVersion;
import io.spine.client.QueryResponse;
import io.spine.core.Response;
import io.spine.core.Version;
import io.spine.testing.server.blackbox.BbTask;
import io.spine.testing.server.blackbox.BbTaskId;

import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.testing.server.query.GivenResponseStatus.ok;

final class QueryResultSubjectTestEnv {

    private static final String TASK_1_TITLE = "Clean dishes";
    private static final String TASK_1_DESCRIPTION = "Do clean all the dishes";

    private static final String TASK_2_TITLE = "Do the laundry";
    private static final String TASK_2_DESCRIPTION = "Do the laundry very efficiently";

    /** Prevents instantiation of this test env class. */
    private QueryResultSubjectTestEnv() {
    }

    static QueryResponse responseWithSingleEntity() {
        var stateWithVersion = EntityStateWithVersion.newBuilder()
                .setState(pack(state1()))
                .setVersion(version1())
                .build();
        var queryResponse = QueryResponse.newBuilder()
                .setResponse(responseOk())
                .addMessage(stateWithVersion)
                .build();
        return queryResponse;
    }

    static QueryResponse responseWithMultipleEntities() {
        var stateWithVersion1 = EntityStateWithVersion.newBuilder()
                .setState(pack(state1()))
                .setVersion(version1())
                .build();
        var stateWithVersion2 = EntityStateWithVersion.newBuilder()
                .setState(pack(state2()))
                .setVersion(version2())
                .build();
        var queryResponse = QueryResponse.newBuilder()
                .setResponse(responseOk())
                .addMessage(stateWithVersion1)
                .addMessage(stateWithVersion2)
                .build();
        return queryResponse;
    }

    private static BbTask state1() {
        var id = BbTaskId.newBuilder()
                .setUuid(newUuid())
                .build();
        var state = BbTask.newBuilder()
                .setTaskId(id)
                .setTitle(TASK_1_TITLE)
                .setDescription(TASK_1_DESCRIPTION)
                .build();
        return state;
    }

    static BbTask state2() {
        var id = BbTaskId.newBuilder()
                .setUuid(newUuid())
                .build();
        var state = BbTask.newBuilder()
                .setTaskId(id)
                .setTitle(TASK_2_TITLE)
                .setDescription(TASK_2_DESCRIPTION)
                .build();
        return state;
    }

    private static Version version1() {
        return Version.newBuilder()
                .setNumber(15)
                .setTimestamp(currentTime())
                .build();
    }

    static Version version2() {
        return Version.newBuilder()
                .setNumber(42)
                .setTimestamp(currentTime())
                .build();
    }

    private static Response responseOk() {
        return Response.newBuilder()
                .setStatus(ok())
                .build();
    }
}
