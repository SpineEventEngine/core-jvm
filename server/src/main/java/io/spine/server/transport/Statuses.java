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

package io.spine.server.transport;

import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.spine.annotation.Internal;
import io.spine.core.ErrorCase;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.ErrorCase.ERROR_CASE_UNSPECIFIED;
import static io.spine.core.ErrorCase.INTERNAL_ERROR;
import static io.spine.core.ErrorCase.UNRECOGNIZED;
import static io.spine.core.Responses.errorWith;
import static io.spine.protobuf.AnyPacker.pack;

/**
 * Utility class for working with {@link Status}es.
 */
@Internal
public final class Statuses {

    /** Prevent instantiation of this utility class. */
    private Statuses() {
    }

    /**
     * Creates an exception that fails a call with an error of the given kind.
     *
     * <p>The status of the exception has a code that depends only on the error case, and
     * no description. The {@code grpc-status-details-bin} trailer carries
     * a {@code google.rpc.Status} with a packed {@link io.spine.core.Status spine.core.Status}
     * in its details, which tells the error case. Nothing else about the error is sent.
     *
     * <p>The gRPC code is {@code INVALID_ARGUMENT} for the errors of a request,
     * {@code PERMISSION_DENIED} for a denied request, and {@code INTERNAL} for
     * an internal error.
     *
     * @param errorCase
     *         the kind of the error; an unspecified one is reported as
     *         {@link ErrorCase#INTERNAL_ERROR INTERNAL_ERROR}
     * @return the exception to pass to {@code StreamObserver.onError()}
     */
    public static StatusRuntimeException failure(ErrorCase errorCase) {
        checkNotNull(errorCase);
        var reported = isSpecified(errorCase) ? errorCase : INTERNAL_ERROR;
        var status = com.google.rpc.Status.newBuilder()
                .setCode(codeOf(reported).value())
                .addDetails(pack(errorWith(reported)))
                .build();
        return StatusProto.toStatusRuntimeException(status);
    }

    private static Code codeOf(ErrorCase errorCase) {
        return switch (errorCase) {
            case INVALID_REQUEST, UNSUPPORTED_REQUEST, TENANT_ERROR -> Code.INVALID_ARGUMENT;
            case REQUEST_DENIED -> Code.PERMISSION_DENIED;
            case INTERNAL_ERROR, ERROR_CASE_UNSPECIFIED, UNRECOGNIZED -> Code.INTERNAL;
        };
    }

    private static boolean isSpecified(ErrorCase errorCase) {
        return errorCase != ERROR_CASE_UNSPECIFIED && errorCase != UNRECOGNIZED;
    }
}
