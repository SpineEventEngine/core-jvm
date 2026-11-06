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

import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.Internal;
import io.spine.base.Error;
import io.spine.core.ErrorCase;
import io.spine.core.Response;
import io.spine.core.Responses;
import io.spine.core.Status;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.ErrorCase.ERROR_CASE_UNSPECIFIED;
import static io.spine.core.ErrorCase.UNRECOGNIZED;
import static io.spine.core.Responses.ok;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * A utility class for the routines related to
 * {@linkplain StreamObserver gRPC StreamObserver instances}.
 */
public final class StreamObservers {

    /** Prevents instantiation of this utility class. */
    private StreamObservers() {
    }

    /**
     * Creates a {@linkplain StreamObserver observer} that does nothing upon
     * the invocation of its callback methods.
     *
     * <p>The callees that do not want to follow the responses should use this utility method
     * to eliminate boilerplate code.
     *
     * @return an instance of {@code StreamObserver} that does nothing
     */
    public static <T> StreamObserver<T> noOpObserver() {
        return new NoOpObserver<>();
    }

    /**
     * A utility method that sends an {@linkplain Responses#ok() acknowledgement}
     * to the client via the {@code responseObserver} provided and
     * {@linkplain StreamObserver#onCompleted() completes} the response.
     */
    public static void ack(StreamObserver<Response> responseObserver) {
        responseObserver.onNext(ok());
        responseObserver.onCompleted();
    }

    /**
     * Wraps the given {@code delegate} into a {@code StreamObserver} and proxies only errors to it.
     *
     * @param delegate the delegate observer
     * @param <T>      generic parameter type of the delegating observer to be created.
     * @return a delegating observer that only proxies errors.
     */
    @Internal
    public static <T> StreamObserver<T> forwardErrorsOnly(StreamObserver<?> delegate) {
        return new ErrorForwardingObserver<>(delegate);
    }

    /**
     * Creates an instance of observer that memoizes the responses.
     *
     * @param <T> type of objects streamed to this observer
     * @return a memoizing observer
     */
    @Internal
    public static <T> MemoizingObserver<T> memoizingObserver() {
        return new MemoizingObserver<>();
    }

    /**
     * Obtains the {@linkplain ErrorCase kind of the error} from
     * the {@linkplain StreamObserver#onError(Throwable) Throwable} received on the client side
     * as a result of a failed gRPC call to the server.
     *
     * <p>The error case comes in the {@code grpc-status-details-bin} trailer:
     * a {@code google.rpc.Status} with a packed {@link Status spine.core.Status} in its details.
     *
     * @param throwable
     *         the {@code Throwable} to obtain the error case from
     * @return the error case, or {@code Optional.empty()} if the throwable carries none
     */
    public static Optional<ErrorCase> errorCaseOf(Throwable throwable) {
        checkNotNull(throwable);
        var status = StatusProto.fromThrowable(throwable);
        if (status == null) {
            return Optional.empty();
        }
        return status.getDetailsList()
                     .stream()
                     .filter(details -> details.is(Status.class))
                     .map(details -> unpack(details, Status.class).getErrorCase())
                     .filter(errorCase -> errorCase != ERROR_CASE_UNSPECIFIED
                             && errorCase != UNRECOGNIZED)
                     .findFirst();
    }

    /**
     * Extracts a {@linkplain Error system error} from the
     * {@linkplain StreamObserver#onError(Throwable) Throwable}
     * received on a client-side as a result of a failed gRPC call to server-side routines.
     *
     * <p>The {@code Error} is extracted from the trailer metadata of
     * either {@link StatusRuntimeException} or {@link StatusException} only.
     *
     * <p>If any other type of {@code Throwable} is passed, {@code Optional.empty()} is returned.
     *
     * @param throwable the {@code Throwable} to extract an {@link Error} from
     * @return the extracted error or {@code Optional.empty()} if the extraction failed
     * @deprecated The server sends only the kind of an error; please use
     *         {@link #errorCaseOf(Throwable)}.
     */
    @Deprecated
    @SuppressWarnings("ChainOfInstanceofChecks") // Only way to check an exact throwable type.
    public static Optional<Error> fromStreamError(Throwable throwable) {
        checkNotNull(throwable);
        if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            var metadata = statusRuntimeException.getTrailers();
            return metadata == null
                   ? Optional.empty()
                   : MetadataConverter.toError(metadata);
        }
        if (throwable instanceof StatusException statusException) {
            var metadata = statusException.getTrailers();
            return metadata == null
                ? Optional.empty()
                : MetadataConverter.toError(metadata);
        }
        return Optional.empty();
    }

    /**
     * An observer that does nothing.
     *
     * @param <T> the type of the observable value
     */
    private static class NoOpObserver<T> implements StreamObserver<T> {

        @Override
        public void onNext(T value) {
            // Do nothing.
        }

        @Override
        public void onError(Throwable t) {
            // Do nothing.
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }

        @Override
        public String toString() {
            return "StreamObservers.noOpObserver()";
        }
    }

    /**
     * An observer that forwards error handling to the passed delegate.
     * Otherwise does nothing.
     *
     * @param <T> type of the observable value
     */
    private static class ErrorForwardingObserver<T> extends DelegatingObserver<T> {

        private ErrorForwardingObserver(StreamObserver<?> delegate) {
            super(cast(delegate));
        }

        @SuppressWarnings("unchecked") // OK since the generic API is never used.
        private static <T> StreamObserver<T> cast(StreamObserver<?> delegate) {
            return (StreamObserver<T>) checkNotNull(delegate);
        }

        @Override
        public void onNext(T value) {
            // do nothing.
        }

        @Override
        public void onCompleted() {
            // do nothing.
        }

        @Override
        public String toString() {
            return "StreamObservers.forwardErrorsOnly()";
        }
    }
}
