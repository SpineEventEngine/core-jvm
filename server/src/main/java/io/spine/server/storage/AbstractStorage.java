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

/**
 * Abstract base for storages.
 *
 * <p>A storage can read and write messages of the given type.
 *
 * @param <I> the type of IDs of storage records
 * @param <M> the type of records kept in the storage
 */
public abstract class AbstractStorage<I, M extends Message> implements Storage<I, M> {

    private final boolean multiTenant;
    private boolean open = true;

    protected AbstractStorage(boolean multiTenant) {
        this.multiTenant = multiTenant;
    }

    @Override
    public boolean isMultiTenant() {
        return multiTenant;
    }

    /**
     * Ensures the storage is not closed.
     *
     * <p>If the storage is closed, throws {@code IllegalStateException} with the passed message.
     *
     * @param message exception message
     * @throws IllegalStateException if the storage is closed
     */
    protected final void checkNotClosed(String message) throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Ensures the storage is not closed.
     *
     * <p>If the storage is closed, throws {@code IllegalStateException}.
     *
     * @throws IllegalStateException if the storage is closed
     */
    protected final void checkNotClosed() throws IllegalStateException {
        checkNotClosed("The storage is closed.");
    }

    /**
     * Tests whether the storage is open.
     *
     * @return {@code true} if the storage is open for writing (wasn't closed until now),
     *         {@code false} otherwise
     * @see #close()
     */
    @Override
    public boolean isOpen() {
        return this.open;
    }

    /**
     * Tests whether the storage is closed.
     *
     * @return {@code true} if the storage is closed, {@code false} otherwise
     * @see #close()
     */
    public final boolean isClosed() {
        return !isOpen();
    }

    /**
     * Closes the storage.
     *
     * @throws IllegalStateException if the storage was already closed
     */
    @Override
    public void close() {
        checkNotClosed();
        this.open = false;
    }
}
