/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;

import java.util.Iterator;

/**
 * A record storage made suitable for testing the {@link MessageStorage} API by
 * exposing the methods originally designed as {@code protected} to {@code public}.
 *
 * @param <I>
 *         the type of record identifiers
 * @param <M>
 *         the type of stored {@code Message} records
 */
public abstract class RecordStorageUnderTest<I, M extends Message>
        extends MessageStorage<I, M> {

    /**
     * Creates a new instance.
     *
     * @param context
     *         a specification of Bounded Context in which the created storage is used
     * @param delegate
     *         the record storage to delegate the execution to
     */
    protected RecordStorageUnderTest(ContextSpec context, RecordStorage<I, M> delegate) {
        super(context, delegate);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method as a part of {@code public} API.
     */
    @Override
    public void writeBatch(Iterable<M> messages) {
        super.writeBatch(messages);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method as a part of {@code public} API.
     */
    @Override
    public Iterator<M> readAll() {
        return super.readAll();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method as a part of {@code public} API.
     */
    @Override
    public Iterator<M> readAll(RecordQuery<I, M> query) {
        return super.readAll(query);
    }
}
