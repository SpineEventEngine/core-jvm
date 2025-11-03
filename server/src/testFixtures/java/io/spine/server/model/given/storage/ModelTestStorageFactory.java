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

package io.spine.server.model.given.storage;

import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link StorageFactory} which configures the inbox storage
 * to {@linkplain NeverForgettingStorage persist} every record even after
 * {@code delete(..)} is called.
 */
public final class ModelTestStorageFactory implements StorageFactory {

    private final StorageFactory delegate;

    public ModelTestStorageFactory(StorageFactory delegate) {
        this.delegate = checkNotNull(delegate);
    }

    @Override
    public <I, R extends Message> RecordStorage<I, R> createRecordStorage(
            ContextSpec context,
            RecordSpec<I, R> recordSpec
    ) {
        var storage = delegate.createRecordStorage(context, recordSpec);
        if (recordSpec.recordType().equals(InboxMessage.class)) {
            return new NeverForgettingStorage<>(context, storage);
        }
        return storage;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
