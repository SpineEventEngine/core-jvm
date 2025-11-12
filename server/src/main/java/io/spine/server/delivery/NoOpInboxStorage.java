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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * An {@code InboxStorage} which does nothing.
 *
 * <p>To be used strictly in a {@linkplain Delivery#direct() direct} delivery mode,
 * which assumes that dispatched signals should skip their corresponding {@code Inbox}es.
 */
final class NoOpInboxStorage extends InboxStorage {

    private static @MonotonicNonNull NoOpInboxStorage instance = null;

    private NoOpInboxStorage() {
        super(InMemoryStorageFactory.newInstance(), false);
    }

    /**
     * Returns the singleton instance of {@code NoOpInboxStorage}.
     */
    static synchronized NoOpInboxStorage instance() {
        if (instance == null) {
            instance = new NoOpInboxStorage();
        }
        return instance;
    }

    /**
     * Always returns {@code Optional.empty()}.
     */
    @Override
    public Optional<InboxMessage> read(InboxMessageId id) {
        return Optional.empty();
    }

    /**
     * Always returns an empty page of messages.
     */
    @Override
    public Page<InboxMessage> readAll(ShardIndex index, int pageSize) {
        return EmptyPage.instance();
    }

    /**
     * Always returns an empty list of messages.
     */
    @Override
    public ImmutableList<InboxMessage>
    readAll(ShardIndex index, @Nullable Timestamp sinceWhen, int pageSize) {
        return ImmutableList.of();
    }

    /**
     * Always returns {@code Optional.empty()}.
     */
    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex index) {
        return Optional.empty();
    }

    /**
     * Does nothing.
     */
    @Override
    public synchronized void write(InboxMessageId id, InboxMessage message) {
        // Do nothing.
    }

    /**
     * A page of {@code InboxMessage}s which is always empty.
     */
    private static class EmptyPage implements Page<InboxMessage> {

        private static @MonotonicNonNull EmptyPage instance = null;

        private static synchronized EmptyPage instance() {
            if (instance == null) {
                instance = new EmptyPage();
            }
            return instance;
        }

        @Override
        public ImmutableList<InboxMessage> contents() {
            return ImmutableList.of();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Optional<Page<InboxMessage>> next() {
            return Optional.empty();
        }
    }
}
