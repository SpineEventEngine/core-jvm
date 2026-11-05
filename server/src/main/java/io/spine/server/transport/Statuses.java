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
import io.spine.core.Responses;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.ErrorCase.INTERNAL_ERROR;
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
     * Creates an exception to fail a call with an error of the given kind.
     *
     * <p>The status of the exception has the {@linkplain #codeOf(ErrorCase) code} of
     * the error case, and no description. The {@code grpc-status-details-bin} trailer carries
     * a {@code google.rpc.Status} with a packed {@link io.spine.core.Status spine.core.Status}
     * in its details, which tells the error case. Nothing else about the error reaches
     * the client.
     *
     * @param errorCase
     *         the kind of the error; an unspecified one is reported as
     *         {@link ErrorCase#INTERNAL_ERROR}
     * @return the exception to pass to {@code StreamObserver.onError()}
     */
    public static StatusRuntimeException failure(ErrorCase errorCase) {
        checkNotNull(errorCase);
        var reported = isSpecified(errorCase) ? errorCase : INTERNAL_ERROR;
        var status = com.google.rpc.Status.newBuilder()
                .setCode(codeOf(reported).value())
                .addDetails(pack(Responses.errorWith(reported)))
                .build();
        return StatusProto.toStatusRuntimeException(status);
    }

    /**
     * Obtains the gRPC status code for the given error case.
     *
     * <p>Several error cases share {@code INVALID_ARGUMENT}, so the code does not reveal more
     * than the error case does.
     */
    public static Code codeOf(ErrorCase errorCase) {
        checkNotNull(errorCase);
        return switch (errorCase) {
            case INVALID_REQUEST, UNSUPPORTED_REQUEST, TENANT_ERROR -> Code.INVALID_ARGUMENT;
            case REQUEST_DENIED -> Code.PERMISSION_DENIED;
            case UNKNOWN_SUBSCRIPTION -> Code.NOT_FOUND;
            case INTERNAL_ERROR, ERROR_CASE_UNSPECIFIED, UNRECOGNIZED -> Code.INTERNAL;
        };
    }

    private static boolean isSpecified(ErrorCase errorCase) {
        return errorCase != ErrorCase.ERROR_CASE_UNSPECIFIED
                && errorCase != ErrorCase.UNRECOGNIZED;
    }
}
