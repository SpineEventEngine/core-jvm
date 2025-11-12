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
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;

import java.util.Iterator;
import java.util.Optional;

/**
 * A {@link RecordStorage} which delegates all of its operations to another instance
 * of {@code RecordStorage}.
 *
 * <p>The framework code deals with many objects that need to be stored. Some of them are
 * the records of {@code Entity} states, some aren't (e.g. {@code Event}s). The concrete
 * storage implementations for these objects vary in API and functionality. However, neither
 * of them performs the save/load operations themselves. Instead, they create a separate
 * {@code RecordStorage}, configure it according to the properties of respective stored objects,
 * and use it as a delegate. This type serves as a base for all such storages.
 *
 * <p>Such an approach standardizes the way to store and query all objects in the system. It makes
 * the creation of a new storage type as simple as extending this type and passing
 * the {@link RecordSpec} corresponding to the stored object, into its
 * {@code super(..)} constructor.
 *
 * <p>In order to allow the descendants to configure the visibility of their API themselves,
 * most of the methods of this type is made {@code protected}. The particular implementations
 * are able to override these methods with the required level of visibility, if needed.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the message records
 */
public abstract class RecordStorageDelegate<I, R extends Message> extends RecordStorage<I, R> {

    private final RecordStorage<I, R> delegate;

    /**
     * Initializes this storage with the instance to delegate the execution of operations to.
     *
     * @param context
     *         specification of Bounded Context in scope of which this storage is used
     * @param delegate
     *         storage instance to delegate all operations to
     */
    protected RecordStorageDelegate(ContextSpec context, RecordStorage<I, R> delegate) {
        super(context, delegate.recordSpec());
        this.delegate = delegate;
    }

    @Override
    public Iterator<I> index(RecordQuery<I, R> query) {
        return delegate.index(query);
    }

    @Override
    public Optional<R> read(I id) {
        return delegate.read(id);
    }

    @Override
    protected Optional<R> read(I id, FieldMask mask) {
        return delegate.read(id, mask);
    }

    @Override
    protected Iterator<R> readAll() {
        return delegate.readAll();
    }

    @Override
    protected Iterator<R> readAll(Iterable<I> ids) {
        return delegate.readAll(ids);
    }

    @Override
    protected Iterator<R> readAll(Iterable<I> ids, FieldMask mask) {
        return delegate.readAll(ids, mask);
    }

    @Override
    protected Iterator<R> readAll(RecordQuery<I, R> query) {
        return delegate.readAll(query);
    }

    @Override
    protected void write(RecordWithColumns<I, R> record) {
        delegate.write(record);
    }

    @Override
    protected void writeAll(Iterable<? extends RecordWithColumns<I, R>> records) {
        delegate.writeAll(records);
    }

    @Override
    public void write(I id, R record) {
        delegate.write(id, record);
    }

    @Override
    protected void writeRecord(RecordWithColumns<I, R> record) {
        delegate.writeRecord(record);
    }

    @Override
    @Internal
    protected void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records) {
        delegate.writeAllRecords(records);
    }

    @Override
    @CanIgnoreReturnValue
    protected boolean delete(I id) {
        return delegate.delete(id);
    }

    @Override
    protected void deleteAll(Iterable<I> ids) {
        delegate.deleteAll(ids);
    }

    @Override
    protected RecordQuery<I, R> toQuery(I id) {
        return delegate.toQuery(id);
    }

    @Override
    protected RecordQuery<I, R> toQuery(I id, FieldMask mask) {
        return delegate.toQuery(id, mask);
    }

    @Override
    protected RecordQuery<I, R> toQuery(Iterable<I> ids) {
        return delegate.toQuery(ids);
    }

    @Override
    protected RecordQuery<I, R> toQuery(Iterable<I> ids, FieldMask mask) {
        return delegate.toQuery(ids, mask);
    }

    @Override
    protected RecordQuery<I, R> queryForAll() {
        return delegate.queryForAll();
    }

    @Override
    protected Iterator<R> readAllRecords(RecordQuery<I, R> query) {
        return delegate.readAllRecords(query);
    }

    @Override
    @CanIgnoreReturnValue
    protected boolean deleteRecord(I id) {
        return delegate.deleteRecord(id);
    }

    @Override
    @Internal
    protected RecordSpec<I, R> recordSpec() {
        return delegate.recordSpec();
    }

    @Override
    public final boolean isMultitenant() {
        return delegate.isMultitenant();
    }

    @Override
    public final boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public Iterator<I> index() {
        return delegate.index();
    }
}
