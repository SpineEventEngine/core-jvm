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
    boolean isMultiTenant();

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
