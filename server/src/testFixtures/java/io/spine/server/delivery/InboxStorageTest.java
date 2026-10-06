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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;
import io.spine.server.ServerEnvironment;
import io.spine.server.storage.AbstractStorageTest;
import io.spine.test.delivery.AddNumber;
import io.spine.test.delivery.Calc;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static com.google.protobuf.util.Durations.fromSeconds;
import static com.google.protobuf.util.Timestamps.add;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.server.delivery.DeliveryStrategy.newIndex;
import static io.spine.server.delivery.InboxIds.newSignalId;
import static io.spine.server.delivery.InboxMessageStatus.DELIVERED;
import static io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static io.spine.server.delivery.given.TestInboxMessages.copyWithStatus;
import static io.spine.server.delivery.given.TestInboxMessages.toDeliver;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An abstract base for tests of {@link InboxStorage} implementations.
 */
@DisplayName("`InboxStorage` should")
public abstract class InboxStorageTest
        extends AbstractStorageTest<InboxMessageId, InboxMessage, InboxStorage> {

    private static final String TARGET_ID = "the-storage-calc";

    private final TestActorRequestFactory factory =
            new TestActorRequestFactory(InboxStorageTest.class);
    private final SecureRandom random = new SecureRandom();

    @Override
    protected InboxStorage newStorage() {
        return ServerEnvironment.instance()
                                .storageFactory()
                                .createInboxStorage(false);
    }

    @Override
    protected InboxMessage newStorageRecord(InboxMessageId id) {
        return newCommandInInbox(id, TARGET_ID);
    }

    @Override
    protected InboxMessageId newId() {
        var index = newIndex(4, 2020);
        return InboxMessageMixin.generateIdWith(index);
    }

    @Test
    @DisplayName("write and read `InboxMessage`s")
    void writeAndReadRecords() {
        var index = newIndex(1, 100);
        assertStorageEmpty(index);

        var messages = generateMessages(index, 256);

        var firstMessage = messages.get(0);

        storage().write(firstMessage);
        var pageWithSingleRecord = readContents(index);
        assertThat(pageWithSingleRecord.contents()
                                       .size()).isEqualTo(1);
        assertThat(pageWithSingleRecord.contents()
                                       .get(0)).isEqualTo(firstMessage);

        storage().writeBatch(messages.subList(1, messages.size()));
        var pageWithAllRecords = readContents(index);
        assertSameContent(messages, pageWithAllRecords);
    }

    @Test
    @DisplayName("read `InboxMessage`s page by page starting from the older messages")
    void readRecordsPageByPage() {
        var index = newIndex(8, 2019);
        var totalMessages = 79;
        var pageSize = 13;

        var messages = generateMessages(index, totalMessages);
        storage().writeBatch(messages);
        storage().writeBatch(generateMessages(newIndex(19, 2019), 19));
        storage().writeBatch(generateMessages(newIndex(21, 2019), 23));

        var expected = Lists.partition(messages, pageSize);
        var actualPage = storage().readAll(index, pageSize);
        for (var iterator = expected.iterator(); iterator.hasNext(); ) {
            var expectedPage = iterator.next();
            assertSameContent(expectedPage, actualPage);

            var maybeNext = actualPage.next();
            if (iterator.hasNext()) {
                assertThat(maybeNext).isPresent();
                actualPage = maybeNext.get();
            } else {
                assertThat(maybeNext).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("remove selected `InboxMessage` instances")
    void removeMessages() {
        var index = newIndex(6, 7);
        var messages = generate(20, index);
        var storage = storage();
        storage.writeBatch(messages);

        readAllAndCompare(storage, index, messages);

        var iterator = messages.iterator();
        var first = iterator.next();
        var second = iterator.next();

        storage.removeBatch(ImmutableList.of(first, second));

        // Make a `List` from the rest of the elements. Those deleted aren't included.
        var remainder = ImmutableList.copyOf(iterator);

        readAllAndCompare(storage, index, remainder);

        storage.removeBatch(remainder);
        checkEmpty(storage, index);
    }

    @Test
    @DisplayName("do nothing if removing inexistent `InboxMessage` instances")
    void doNothingIfRemovingInexistentMessages() {

        var storage = storage();
        var index = newIndex(6, 7);
        checkEmpty(storage, index);

        var messages = generate(40, index);
        storage.removeBatch(messages);

        checkEmpty(storage, index);
    }

    @Test
    @DisplayName("read the messages to deliver received no later than the given time, oldest first")
    void readMessagesToDeliverUpToTime() {
        var shard = 5;
        var shardCount = 7;
        var now = currentTime();
        var oldest = generate(shard, shardCount, subtract(now, fromSeconds(3)));
        var older = withVersion(generate(shard, shardCount, subtract(now, fromSeconds(2))), 1);
        var olderOfHigherVersion = withVersion(
                generate(shard, shardCount, older.getWhenReceived()), 2);
        var atTheTime = generate(shard, shardCount, now);
        var later = generate(shard, shardCount, add(now, fromSeconds(1)));
        var delivered = copyWithStatus(generate(shard, shardCount, older.getWhenReceived()),
                                       DELIVERED);
        var toCatchUp = copyWithStatus(generate(shard, shardCount, older.getWhenReceived()),
                                       TO_CATCH_UP);
        var inOtherShard = generate(shard + 1, shardCount, older.getWhenReceived());
        var storage = storage();
        storage.writeBatch(ImmutableList.of(later, atTheTime, olderOfHigherVersion, delivered,
                                            toCatchUp, older, inOtherShard, oldest));

        var found = storage.readToDeliver(newIndex(shard, shardCount), now);

        assertThat(found).containsExactly(oldest, older, olderOfHigherVersion, atTheTime)
                         .inOrder();
    }

    private static InboxMessage withVersion(InboxMessage message, int version) {
        return message.toBuilder()
                      .setVersion(version)
                      .build();
    }

    @Test
    @DisplayName("mark messages delivered")
    void markMessagedDelivered() {
        var index = newIndex(3, 71);
        var messages = generate(10, index);
        var storage = storage();
        storage.writeBatch(messages);

        var nonDelivered = readAllAndCompare(storage, index, messages);
        nonDelivered.iterator()
                    .forEachRemaining((m) -> assertEquals(TO_DELIVER, m.getStatus()));

        // Leave the first one in `TO_DELIVER` status and mark the rest as `DELIVERED`.
        var iterator = messages.iterator();
        var remainingNonDelivered = iterator.next();
        var toMarkDelivered = ImmutableList.copyOf(iterator);
        var markedDelivered = markDelivered(toMarkDelivered);

        storage.writeBatch(markedDelivered);
        var originalMarkedDelivered = toMarkDelivered.stream()
                .map(m -> m.toBuilder()
                        .setStatus(DELIVERED)
                        .build())
                .collect(toImmutableList());

        // Check that both `TO_DELIVER` message and those marked `DELIVERED` are stored as expected.
        var readResult = storage.readAll(index, Integer.MAX_VALUE)
                                .contents();
        assertTrue(readResult.contains(remainingNonDelivered));
        assertTrue(readResult.containsAll(originalMarkedDelivered));
    }

    private static List<InboxMessage> markDelivered(ImmutableList<InboxMessage> toMarkDelivered) {
        return toMarkDelivered.stream()
                .map(m -> m.toBuilder()
                        .setStatus(DELIVERED)
                        .build())
                .collect(toList());
    }

    /*
     * Test environment and utilities.
     *
     * Some of the package-private utilities are accessed in this section.
     * This is why it is not extracted into a separate `TestEnv`.
     ******************************************************************************/

    private static void assertSameContent(Collection<InboxMessage> expected,
                                          Page<InboxMessage> page) {
        assertThat(page.contents()).containsExactlyElementsIn(expected);
    }

    private ImmutableList<InboxMessage> generateMessages(ShardIndex index, int count) {
        ImmutableList.Builder<InboxMessage> msgBuilder = ImmutableList.builder();
        IntStream.range(0, count)
                 .forEach(
                         (i) -> {
                             msgBuilder.add(newCommandInInbox(index, TARGET_ID));
                             // Sleep to distinguish the messages by their `when_received` values.
                             sleepUninterruptibly(Duration.ofMillis(1));
                         }
                 );
        return msgBuilder.build();
    }

    private Page<InboxMessage> readContents(ShardIndex index) {
        return storage().readAll(index, Integer.MAX_VALUE);
    }

    private void assertStorageEmpty(ShardIndex index) {
        var page = readContents(index);
        assertThat(page.contents()
                       .isEmpty()).isTrue();
        assertThat(page.next()).isEmpty();
    }

    private InboxMessage newCommandInInbox(ShardIndex index, String targetId) {
        return newCommandInInbox(InboxMessageMixin.generateIdWith(index), targetId);
    }

    private InboxMessage newCommandInInbox(InboxMessageId id, String targetId) {
        var command = factory.createCommand(AddNumber.newBuilder()
                                                         .setCalculatorId(targetId)
                                                         .setValue(random.nextInt())
                                                         .build());
        var inboxId = InboxIds.wrap(targetId, TypeUrl.of(Calc.class));
        var signalId = newSignalId(targetId, command.getId().value());
        return InboxMessage.newBuilder()
                .setId(id)
                .setSignalId(signalId)
                .setInboxId(inboxId)
                .setLabel(InboxLabel.HANDLE_COMMAND)
                .setStatus(TO_DELIVER)
                .setCommand(command)
                .setWhenReceived(Time.currentTime())
                .build();
    }

    @CanIgnoreReturnValue
    private static ImmutableList<InboxMessage>
    readAllAndCompare(InboxStorage storage, ShardIndex idx, ImmutableList<InboxMessage> expected) {
        var page = storage.readAll(idx, Integer.MAX_VALUE);
        assertEquals(expected.size(), page.size());

        var contents = page.contents();
        assertEquals(ImmutableSet.copyOf(expected), ImmutableSet.copyOf(contents));
        return contents;
    }

    private static void checkEmpty(InboxStorage storage, ShardIndex index) {
        var emptyPage = storage.readAll(index, 10);
        assertEquals(0, emptyPage.size());
        assertThat(emptyPage.contents()).isEmpty();
        assertThat(emptyPage.next()).isEmpty();
    }

    /**
     * Generates an {@link InboxMessage} with the specified values.
     *
     * <p>The message values are set as if it was received at {@code whenReceived} time
     * and its status was {@link InboxMessageStatus#TO_DELIVER TO_DELIVER}.
     */
    public static InboxMessage generate(int shardIndex, int totalShards, Timestamp whenReceived) {
        checkNotNull(whenReceived);
        var message = toDeliver("target-entity-id", TypeUrl.of(Calc.class), whenReceived);

        var modifiedId = message.getId()
                .toBuilder()
                .setIndex(newIndex(shardIndex, totalShards))
                .build();
        var result = message.toBuilder()
                .setId(modifiedId)
                .build();
        return result;
    }

    /**
     * Generates {@code totalMessages} in a selected shard.
     *
     * <p>Each message is generated as received {@code now} and in
     * {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} status.
     */
    public static ImmutableList<InboxMessage> generate(int totalMessages, ShardIndex index) {
        ImmutableList.Builder<InboxMessage> builder = ImmutableList.builder();
        for (var msgCounter = 0; msgCounter < totalMessages; msgCounter++) {

            var msg = generate(index.getIndex(), index.getOfTotal(), currentTime());
            builder.add(msg);
        }
        return builder.build();
    }
}
