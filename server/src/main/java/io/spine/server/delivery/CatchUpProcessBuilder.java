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

package io.spine.server.delivery;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.server.delivery.CatchUpProcess.DispatchCatchingUp;
import io.spine.server.projection.ProjectionRepository;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkPositive;

/**
 * A builder for {@link CatchUpProcess}.
 *
 * @param <I>
 *         the type of identifiers of the processed projections
 */
public final class CatchUpProcessBuilder<I> {

    private final ProjectionRepository<I, ?, ?> repository;
    private @MonotonicNonNull CatchUpStorage storage;
    private @MonotonicNonNull DispatchCatchingUp<I> dispatchOp;
    private int pageSize;

    /**
     * Creates an new instance of the builder.
     *
     * @param repository
     *         repository of the projection which catch-up process is being built
     */
    CatchUpProcessBuilder(ProjectionRepository<I, ?, ?> repository) {
        this.repository = repository;
    }

    /**
     * Obtains the projection repository for which the catch-up process is being built.
     */
    public ProjectionRepository<I, ?, ?> getRepository() {
        return repository;
    }

    /**
     * Sets the {@code CatchUpStorage} to use during the catch-up.
     */
    @CanIgnoreReturnValue
    CatchUpProcessBuilder<I> setStorage(CatchUpStorage storage) {
        this.storage = checkNotNull(storage);
        return this;
    }

    /**
     * Checks whether the {@code CatchUpStorage} has been configured.
     *
     * @return {@code true} if the storage was set, {@code false} otherwise
     */
    boolean hasStorage() {
        return storage != null;
    }

    /**
     * Returns the configured {@code CatchUpStorage}.
     *
     * @throws NullPointerException
     *         if the storage has not been set
     */
    CatchUpStorage getStorage() {
        return checkNotNull(storage);
    }

    /**
     * Sets the maximum page size for the {@code EventStore} reads.
     *
     * <p>Must be a positive value.
     */
    @CanIgnoreReturnValue
    CatchUpProcessBuilder<I> setPageSize(int pageSize) {
        checkPositive(pageSize);
        this.pageSize = pageSize;
        return this;
    }

    /**
     * Checks whether the page size has been configured.
     *
     * @return {@code true} if the page size was set, {@code false} otherwise
     */
    boolean hasPageSize() {
        return pageSize > 0;
    }

    /**
     * Obtains the value of the maximum page size for the {@code EventStore} reads.
     */
    int getPageSize() {
        return pageSize;
    }

    /**
     * Sets the way to dispatch the events during the catch-up.
     */
    @CanIgnoreReturnValue
    public CatchUpProcessBuilder<I> setDispatchOp(DispatchCatchingUp<I> operation) {
        this.dispatchOp = checkNotNull(operation);
        return this;
    }

    /**
     * Checks whether the dispatch operation has been configured.
     *
     * @return {@code true} if the dispatch operation was set, {@code false} otherwise
     */
    boolean hasDispatchOp() {
        return dispatchOp != null;
    }

    /**
     * Obtains the pre-configured way to dispatch the events during the catch-up.
     *
     * @throws NullPointerException
     *         if the storage has not been set
     */
    DispatchCatchingUp<I> getDispatchOp() {
        return checkNotNull(dispatchOp);
    }

    /**
     * Creates a new instance of {@code CatchUpProcess}.
     */
    public CatchUpProcess<I> build() {
        checkNotNull(storage);
        checkNotNull(dispatchOp);
        checkPositive(pageSize);

        return new CatchUpProcess<>(this);
    }
}
