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

package io.spine.testing.server.query;

import com.google.protobuf.Empty;
import io.spine.core.Event;
import io.spine.core.Status;

import static io.spine.core.ErrorCase.INVALID_REQUEST;

final class GivenResponseStatus {

    /** Prevents instantiation of this test env class. */
    private GivenResponseStatus() {
    }

    static Status ok() {
        var status = Status.newBuilder()
                .setOk(Empty.getDefaultInstance())
                .build();
        return status;
    }

    static Status error() {
        var status = Status.newBuilder()
                .setErrorCase(INVALID_REQUEST)
                .build();
        return status;
    }

    @SuppressWarnings("deprecation") // Builds the deprecated `Status.rejection` until its removal.
    static Status rejection() {
        var status = Status.newBuilder()
                .setRejection(Event.getDefaultInstance())
                .build();
        return status;
    }
}
