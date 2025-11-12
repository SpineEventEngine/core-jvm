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

package io.spine.server.aggregate.given.employee;

import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;

/**
 * In-memory {@code StorageFactory} which substitutes individual storages with the custom ones.
 */
@SuppressWarnings("OverlyComplexAnonymousInnerClass")
public final class PreparedStorageFactory {

    private PreparedStorageFactory() {
    }

    /**
     * Returns in-memory {@code StorageFactory} with the custom {@code InboxStorage}.
     */
    public static StorageFactory with(InboxStorage inboxStorage) {
        return new StorageFactory() {

            private boolean closed;

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() {
                closed = true;
            }

            @Override
            public <I, R extends Message> RecordStorage<I, R> createRecordStorage(
                    ContextSpec context, RecordSpec<I, R> spec) {
                return InMemoryStorageFactory.newInstance().createRecordStorage(context, spec);
            }

            @Override
            public InboxStorage createInboxStorage(boolean multitenant) {
                return inboxStorage;
            }
        };
    }
}
