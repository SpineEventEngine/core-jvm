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

package io.spine.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;

/**
 * Abstract base for client requests that may filter messages by certain criteria.
 *
 * <p>This class warps around {@link TargetBuilder} for providing fluent API for
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
 *         return type covariance
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
     * Obtains the reference to a proper method of {@code ActorRequestFactory} which
     * creates the builder for the request.
     */
    abstract Function<ActorRequestFactory, A> builderFn();

    /**
     * Obtains typed reference to {@code this} builder instance.
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
     * Requests only passed IDs to be included into the result of the request.
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
     * Requests only passed IDs to be included into the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(Message... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Requests only passed IDs to be included into the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(Long... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Requests only passed IDs to be included into the result of the request.
     */
    @CanIgnoreReturnValue
    public B byId(Integer... ids) {
        return withIds(Arrays.asList(ids));
    }

    /**
     * Requests only passed IDs to be included into the result of the request.
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
     * Instructs to populate only fields with the passed names in the results of the request.
     */
    @CanIgnoreReturnValue
    public B withMask(Iterable<String> fieldNames) {
        builder().withMask(fieldNames);
        return self();
    }

    /**
     * Instructs to populate only fields with the passed names in the results of the request.
     */
    @CanIgnoreReturnValue
    public B withMask(String... fieldNames) {
        builder().withMask(fieldNames);
        return self();
    }
}
