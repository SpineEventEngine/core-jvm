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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * An {@code InboxStorage} that does nothing.
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
     * Always returns an empty list of messages.
     */
    @Override
    public ImmutableList<InboxMessage> readToDeliver(ShardIndex index, Timestamp receivedUpTo) {
        return ImmutableList.of();
    }

    /**
     * Does nothing.
     */
    @Override
    public synchronized void write(InboxMessageId id, InboxMessage message) {
        // Do nothing.
    }

    /**
     * A page of {@code InboxMessage}s that is always empty.
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
