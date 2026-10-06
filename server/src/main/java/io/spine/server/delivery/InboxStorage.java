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
import io.spine.annotation.SPI;
import io.spine.query.RecordQueryBuilder;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.MessageStorage;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static io.spine.server.delivery.InboxColumn.inbox_shard;
import static io.spine.server.delivery.InboxColumn.received_at;
import static io.spine.server.delivery.InboxColumn.status;
import static io.spine.server.delivery.InboxColumn.version;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static java.util.stream.Collectors.toList;

/**
 * A contract for storages of {@link Inbox} messages.
 *
 * <p>The records of a storage of this type are spread across shards identified by a
 * {@linkplain ShardIndex shard index}.
 *
 * <p>Typically, the storage instance is specific to the
 * {@linkplain io.spine.server.ServerEnvironment server environment} and is used across
 * {@code BoundedContext}s to store the delivered messages.
 *
 * <p>A {@link Delivery} serves all its shards with a single instance of this storage, so
 * messages may be written and removed by several threads at once. Subclasses should not
 * serialize that access, and the underlying record storage must be thread-safe.
 */
@SPI
public class InboxStorage extends MessageStorage<InboxMessageId, InboxMessage> {

    /**
     * Creates a new instance of this storage.
     *
     * <p>Generally, {@code InboxStorage} instances should be single-tenant only.
     * It is so, because no distinction should be made for processing of {@code InboxMessage}s,
     * since it is batch-based anyway, and splitting batches even more (across tenants)
     * reduces the performance.
     *
     * @param factory
     *         storage factory to create an underlying record storage
     * @param multitenant
     *         whether {@code InboxStorage} should be multi-tenant
     */
    public InboxStorage(StorageFactory factory, boolean multitenant) {
        super(Delivery.contextSpec(multitenant),
              factory.createRecordStorage(Delivery.contextSpec(multitenant), spec()));
    }

    private static RecordSpec<InboxMessageId, InboxMessage> spec() {
        return new RecordSpec<>(InboxMessageId.class,
                                InboxMessage.class,
                                InboxMessage::getId,
                                InboxColumn.definitions());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method to this package.
     */
    @Override
    protected void write(InboxMessage message) {
        super.write(message);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose this method to this package.
     */
    @Override
    protected void writeBatch(Iterable<InboxMessage> messages) {
        super.writeBatch(messages);
    }

    /**
     * Reads the contents of the storage by the given shard index and returns the first page
     * of the results.
     *
     * <p>The older items go first.
     *
     * @param index
     *         the shard index to return the results for
     * @param pageSize
     *         the maximum number of the elements per page
     * @return the first page of the results
     */
    public Page<InboxMessage> readAll(ShardIndex index, int pageSize) {
        Page<InboxMessage> page = new InboxPage(sinceWhen -> readAll(index, sinceWhen, pageSize));
        return page;
    }

    public ImmutableList<InboxMessage>
    readAll(ShardIndex index, @Nullable Timestamp sinceWhen, int pageSize) {
        var builder =
                queryBuilder().where(inbox_shard).is(index);
        if (sinceWhen != null) {
            builder.where(received_at)
                   .isGreaterThan(sinceWhen);
        }
        var query = limitAndOrder(pageSize, builder).build();
        var iterator = readAll(query);
        return ImmutableList.copyOf(iterator);
    }

    private static RecordQueryBuilder<InboxMessageId, InboxMessage>
    limitAndOrder(int pageSize, RecordQueryBuilder<InboxMessageId, InboxMessage> builder) {
        return builder.limit(pageSize)
                      .sortAscendingBy(received_at)
                      .sortAscendingBy(version);
    }

    /**
     * Finds the newest message {@linkplain InboxMessageStatus#TO_DELIVER to deliver}
     * in the given shard.
     *
     * @param index
     *         the shard index to look in
     * @return the message found or {@code Optional.empty()} if there are no messages to deliver
     *         in the specified shard
     */
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex index) {
        var query =
                queryBuilder().where(inbox_shard).is(index)
                              .where(status).is(TO_DELIVER)
                              .sortDescendingBy(received_at)
                              .limit(1)
                              .build();
        var iterator = readAll(query);
        Optional<InboxMessage> result = iterator.hasNext() ? Optional.of(iterator.next())
                                                           : Optional.empty();
        return result;
    }

    /**
     * Reads the messages in the given shard that are still to deliver and were received
     * no later than the given time.
     *
     * <p>The older messages go first. Messages received at the same time are ordered
     * by their version.
     *
     * <p>A {@link Delivery} reads a shard page by page, each next page holding the messages
     * received after the last message of the previous page. This method finds the messages
     * the pages have left behind: those stored only after a page with later messages was
     * read, and those received at the same time as the last message of a full page.
     *
     * <p>A subclass that reads the messages from elsewhere than the underlying record storage
     * must override this method as well.
     *
     * @param index
     *         the shard index to look in
     * @param receivedUpTo
     *         the latest time, inclusive, at which the messages were received
     * @return the messages found, the older ones first
     */
    public ImmutableList<InboxMessage> readToDeliver(ShardIndex index, Timestamp receivedUpTo) {
        // Sorted as in `newestMessageToDeliver()`, so that a storage needing an index
        // for such queries can serve both of them from one index. The older-first order
        // is applied in memory, with no limit: normally, only a few messages are found.
        var query =
                queryBuilder().where(inbox_shard).is(index)
                              .where(status).is(TO_DELIVER)
                              .where(received_at).isLessOrEqualTo(receivedUpTo)
                              .sortDescendingBy(received_at)
                              .build();
        var newestFirst = readAll(query);
        return stream(newestFirst).sorted(InboxMessageComparator.chronologically)
                                  .collect(toImmutableList());
    }

    /**
     * Removes the passed messages from the storage.
     *
     * <p>Does nothing for messages that aren't in the storage already.
     *
     * @param messages
     *         the messages to remove
     */
    void removeBatch(Iterable<InboxMessage> messages) {
        var toRemove = stream(messages).map(InboxMessage::getId)
                                       .collect(toList());
        deleteAll(toRemove);
    }
}
