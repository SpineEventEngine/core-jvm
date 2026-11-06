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

package io.spine.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Metadata;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Error;
import io.spine.util.Exceptions;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.Metadata.BINARY_BYTE_MARSHALLER;

/**
 * Serves as a converter from {@link Error} to {@link Metadata} and vice versa.
 *
 * @deprecated The server no longer sends an {@code Error} in the metadata of a failed call.
 *         The kind of the error comes in the {@code grpc-status-details-bin} trailer; please
 *         use {@link StreamObservers#errorCaseOf(Throwable)}.
 */
@Internal
@Deprecated
public final class MetadataConverter {

    private static final String ERROR_KEY_NAME = "spine-error-bin";

    /**
     * The {@link Metadata.Key} to store and get an {@link Error} from a {@link Metadata}.
     */
    @VisibleForTesting
    static final Metadata.Key<byte[]> KEY = Metadata.Key.of(ERROR_KEY_NAME, BINARY_BYTE_MARSHALLER);

    /**
     * Prevents instantiation of this utility class.
     */
    private MetadataConverter() {
    }

    /**
     * Returns the {@link Metadata}, containing the {@link Error} as a byte array.
     *
     * @param error the error to convert
     * @return the metadata containing the error
     */
    public static Metadata toMetadata(Error error) {
        checkNotNull(error);
        var metadata = new Metadata();
        metadata.put(KEY, error.toByteArray());
        return metadata;
    }

    /**
     * Returns the {@link Error} extracted from the {@link Metadata}.
     *
     * @param metadata the metadata to convert
     * @return the error extracted from the metadata or {@code Optional.empty()}
     *         if there is no error.
     */
    public static Optional<Error> toError(Metadata metadata) {
        checkNotNull(metadata);
        var bytes = metadata.get(KEY);

        if (bytes == null) {
            return Optional.empty();
        }

        try {
            var error = Error.parseFrom(bytes);
            return Optional.of(error);
        } catch (InvalidProtocolBufferException e) {
            throw Exceptions.illegalStateWithCauseOf(e);
        }
    }
}
