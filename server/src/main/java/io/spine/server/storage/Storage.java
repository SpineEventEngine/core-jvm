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

package io.spine.server.storage;

import com.google.protobuf.Message;
import io.spine.server.Closeable;

import java.util.Iterator;
import java.util.Optional;

/**
 * The base interface for storages.
 *
 * @param <I>
 *         the type of identifiers
 * @param <M>
 *         the type of records
 */
public interface Storage<I, M extends Message> extends Closeable {

    /**
     * Verifies whether the storage is multitenant.
     *
     * @return {@code true} if the storage was created with multitenancy support,
     *         {@code false} otherwise
     */
    boolean isMultitenant();

    /**
     * Returns an iterator over identifiers of records in the storage.
     */
    Iterator<I> index();

    /**
     * Reads a record from the storage by the specified record identifier.
     *
     * @param id
     *         the identifier of the record to read
     * @return a record instance
     *         or {@code Optional.empty()} if there is no record matching this request
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    Optional<M> read(I id);

    /**
     * Writes a record into the storage.
     *
     * <p>Rewrites it if a record with this ID already exists in the storage.
     *
     * @param id
     *         the ID for the record
     * @param record
     *         the record to store
     * @throws IllegalStateException
     *         if the storage is closed
     */
    void write(I id, M record);

    /**
     * Closes the storage.
     *
     * <p>Implementations may throw specific exceptions.
     */
    @Override
    void close();

    /**
     * Verifies whether the storage is open.
     *
     * @return {@code true} if the storage is open, {@code false} otherwise
     */
    @Override
    boolean isOpen();
}
