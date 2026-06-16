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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An abstract base for test suites testing storage implementations.
 *
 * <p>Carries the tests of the API defined in {@link AbstractStorage}. Any custom functionality
 * of the storage-under-test should be implemented by the descendants.
 *
 * <p>This abstract base handles the {@linkplain #newStorage() initialization} and
 * {@linkplain #close(AbstractStorage) closing} of the tested storage instance.
 * So the descendants should omit closing the tested storage instance manually, unless it is
 * required for the particular test scenario.
 *
 * @param <I>
 *         the type of IDs of storage records
 * @param <M>
 *         the type of records kept in the storage
 * @param <S>
 *         the type of the storage under test
 */
public abstract class AbstractStorageTest<I, M extends Message, S extends AbstractStorage<I, M>> {

    private S storage;

    @BeforeEach
    protected void setUpAbstractStorageTest() {
        storage = newStorage();
    }

    @AfterEach
    protected void tearDownAbstractStorageTest() {
        close(storage);
    }

    /**
     * Obtains an instance of the storage-under-test.
     *
     * @return the storage that will be closed automatically after a test
     */
    protected final S storage() {
        return storage;
    }

    /**
     * Creates a new storage instance.
     *
     * <p>The resulting storage should be {@linkplain #close(AbstractStorage) closed} manually to
     * release resources, which may be used by the storage.
     *
     * <p>Use {@link #storage() storage()} method to access the created instance in tests.
     *
     * @return a newly created and empty storage
     * @see AbstractStorage#close()
     */
    protected abstract S newStorage();

    /** Creates a new storage record. */
    protected abstract M newStorageRecord(I id);

    /** Creates a new unique storage record ID. */
    protected abstract I newId();

    /**
     * Closes the storage and propagates an exception if any occurs.
     */
    protected void close(AbstractStorage<?, ?> storage) {
        if (storage.isOpen()) {
            try {
                storage.close();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Closes the storage and fails the test if any exception occurs. */
    @SuppressWarnings("CallToPrintStackTrace")
    private void closeAndFailIfException(AbstractStorage<I, M> storage) {
        try {
            storage.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("An unexpected exception: " + e.getClass() + "; " + e.getMessage());
        }
    }

    /** Writes a record, reads it and asserts it is the same as the expected one. */
    private void writeAndReadRecordTest(I id) {
        var expected = writeRecord(id);

        var actual = storage.read(id);

        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    @CanIgnoreReturnValue
    private M writeRecord(I id) {
        var expected = newStorageRecord(id);
        storage.write(id, expected);
        return expected;
    }

    @Test
    @DisplayName("handle absence of record with passed ID")
    void handleAbsenceOfRecord() {
        var record = storage.read(newId());
        assertResultForMissingId(record);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // This is the purpose of the method.
    private void assertResultForMissingId(Optional<M> record) {
        assertFalse(record.isPresent());
    }

    /**
     * Tests that the storage overwrites the existing record when storing a new record with the
     * same ID.
     *
     * <p>This test should be overridden by the descendants working with storages that are able
     * to store multiple records by the same ID.
     */
    @Test
    @DisplayName("re-write record if writing by the same ID")
    protected void rewriteRecord() {
        var id = newId();
        writeAndReadRecordTest(id);
        writeAndReadRecordTest(id);
    }

    /**
     * Index-based tests here and below aren't grouped into a {@code Nested} test class to allow
     * overriding them in descendants.
     */
    @Test
    @DisplayName("return non-`null` `index()`")
    protected void nonNullIndex() {
        var index = storage.index();
        assertNotNull(index);
    }

    @Test
    @DisplayName("return immutable `index()`")
    protected void immutableIndex() {
        writeRecord(newId());
        assertIndexImmutability();
    }

    protected void assertIndexImmutability() {
        var index = storage.index();
        assertTrue(index.hasNext());
        try {
            index.remove();
            fail("Storage#index is mutable");

            // One of collections used in in-memory implementation throws IllegalStateException
            // but default behavior is UnsupportedOperationException
        } catch (UnsupportedOperationException | IllegalStateException ignored) {
            // One of valid exceptions was thrown
        }
    }

    @Test
    @DisplayName("have all IDs counted by `index()`")
    protected void indexCountingAllIds() {
        var recordCount = 10;
        Set<I> ids = new HashSet<>(recordCount);
        for (var i = 0; i < recordCount; i++) {
            var id = newId();
            writeRecord(id);
            ids.add(id);
        }

        var index = storage.index();
        Collection<I> indexValues = newHashSet(index);

        assertEquals(ids.size(), indexValues.size());
        assertThat(indexValues).containsExactlyElementsIn(ids);
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection"/* Storing of generated objects and
                                                               checking via #contains(Object). */)
    @Test
    @DisplayName("return unique ID")
    void returnUniqueID() {
        var checkCount = 10;
        Set<I> ids = newHashSet();
        for (var i = 0; i < checkCount; i++) {
            var newId = newId();
            if (ids.contains(newId)) {
                fail("AbstractStorageTest.newId() should return unique IDs.");
            }
        }
    }

    @Nested
    @DisplayName("throw NPE when")
    class ThrowNpeIf {

        @Test
        @DisplayName("reading by `null` ID")
        void readByNullId() {
            assertThrows(NullPointerException.class, () -> storage.read(nullRef()));
        }

        @Test
        @DisplayName("writing by `null` ID")
        void writeByNullId() {
            assertThrows(NullPointerException.class,
                         () -> storage.write(nullRef(), newStorageRecord(newId())));
        }

        @Test
        @DisplayName("writing `null` record")
        void writeNullRecord() {
            assertThrows(NullPointerException.class, () -> storage.write(newId(), nullRef()));
        }
    }

    @Nested
    @DisplayName("write and read")
    class WriteAndRead {

        @Test
        @DisplayName("one record")
        void record() {
            writeAndReadRecordTest(newId());
        }

        @Test
        @DisplayName("several records by different IDs")
        void severalRecords() {
            writeAndReadRecordTest(newId());
            writeAndReadRecordTest(newId());
            writeAndReadRecordTest(newId());
        }
    }

    @Nested
    @DisplayName("check storage")
    class CheckStorage {

        @Test
        @DisplayName("not closed")
        void notClosed() {
            storage.checkNotClosed();
        }

        @Test
        @DisplayName("closed")
        void closed() {
            closeAndFailIfException(storage);

            assertThrows(IllegalStateException.class, () -> storage.checkNotClosed());
        }
    }

    @Nested
    @DisplayName("tell if storage is")
    class TellIfStorageIs {

        @Test
        @DisplayName("opened")
        void opened() {
            assertTrue(storage.isOpen());
        }

        @Test
        @DisplayName("not opened")
        void notOpened() {
            storage.close();

            assertFalse(storage.isOpen());
        }

        @Test
        @DisplayName("closed")
        void closed() {
            storage.close();

            assertTrue(storage.isClosed());
        }

        @Test
        @DisplayName("not closed")
        void notClosed() {
            assertFalse(storage.isClosed());
        }
    }

    @Nested
    @DisplayName("close itself and throw ISE on")
    class CloseAndThrowOn {

        @Test
        @DisplayName("read operation")
        void read() {
            closeAndFailIfException(storage);
            assertThrows(IllegalStateException.class, () -> storage.read(newId()));
        }

        @Test
        @DisplayName("write operation")
        void write() {
            closeAndFailIfException(storage);

            var id = newId();
            assertThrows(IllegalStateException.class,
                         () -> storage.write(id, newStorageRecord(id)));
        }

        @Test
        @DisplayName("close operation")
        void close() {
            storage.close();
            assertThrows(IllegalStateException.class, () -> storage.close());
        }
    }
}
