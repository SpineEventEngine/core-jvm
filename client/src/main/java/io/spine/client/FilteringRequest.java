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

package io.spine.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Suppliers2.memoize;

/**
 * Abstract base for client requests that may filter messages by certain criteria.
 *
 * <p>This class wraps around {@link TargetBuilder} for providing fluent API for
 * client request composition and placement.
 *
 * @param <M>
 *         the type of the messages returned by the request
 * @param <R>
 *         the type of the request to be posted
 * @param <A>
 *         the type of the builder of the request
 * @param <B>
 *         the type of this client request (which wraps over type {@code <A>} for
 *         return type covariance)
 */
public abstract class
FilteringRequest<M extends Message,
                 R extends Message,
                 A extends TargetBuilder<R, A>,
                 B extends FilteringRequest<M, R, A, B>>
        extends ClientRequestBase {

    /** The type of messages returned by the request. */
    private final Class<M> messageType;

    /** The request factory configured with the tenant ID and current user ID. */
    private final ActorRequestFactory factory;

    /** Provides the {@linkplain #builderFn() builder} for the request. */
    private final Supplier<A> builder;

    FilteringRequest(ClientRequest parent, Class<M> type) {
        super(parent);
        this.messageType = type;
        this.factory = client().requestOf(user());
        this.builder = memoize(() -> builderFn().apply(factory));
    }

    /**
     * Obtains the reference to a proper method of {@code ActorRequestFactory} that
     * creates the builder for the request.
     */
    abstract Function<ActorRequestFactory, A> builderFn();

    /**
     * Obtains a typed reference to {@code this} builder instance.
     */
    abstract B self();

    /** Obtains the type of the messages returned by the request. */
    final Class<M> messageType() {
        return messageType;
    }

    /**
     * Obtains the builder for the request.
     */
    final A builder() {
        return builder.get();
    }

    @CanIgnoreReturnValue
    private B withIds(Iterable<?> ids) {
        checkNotNull(ids);
        builder().byId(ids);
        return self();
    }

    /**
     * Requests only passed IDs to be included in the result of the request.
     *
     * <p>The calling code must pass identifiers that are of the same type, which also
     * matches the ID type of the requested messages.
     *
     * <p>If the passed iterable is empty, all records matching other criteria will be returned.
     */
    @CanIgnoreReturnValue
    public B byId(Iterable<?> ids) {
        return withIds(ids);
    }

    /**
     * Requests only passed IDs to be included in the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(Message... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Requests only passed IDs to be included in the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(Long... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Requests only passed IDs to be included in the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(Integer... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Requests only passed IDs to be included in the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(String... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Configures the request to return results matching all the passed filters.
     *
     * @deprecated Please use the overloads from the descendants that rely on strongly-typed
     *             filters.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public B where(Filter... filter) {
        builder().where(filter);
        return self();
    }

    /**
     * Configures the request to return results matching all the passed filters.
     *
     * @deprecated Please use the overloads from the descendants that rely on strongly-typed
     *             filters.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public B where(CompositeFilter... filter) {
        builder().where(filter);
        return self();
    }

    /**
     * Does nothing.
     *
     * <p>Formerly, instructed to populate only fields with the passed names in the results
     * of the request.
     *
     * @deprecated Field masks are no longer supported. The results always contain
     *         all the fields. Please remove the call.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public B withMask(Iterable<String> fieldNames) {
        checkNotNull(fieldNames);
        return self();
    }

    /**
     * Does nothing.
     *
     * <p>Formerly, instructed to populate only fields with the passed names in the results
     * of the request.
     *
     * @deprecated Field masks are no longer supported. The results always contain
     *         all the fields. Please remove the call.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public B withMask(String... fieldNames) {
        checkNotNull(fieldNames);
        return self();
    }
}
