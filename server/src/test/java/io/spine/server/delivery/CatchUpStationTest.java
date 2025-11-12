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
import com.google.common.collect.ImmutableSet;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Durations.fromSeconds;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.server.delivery.CatchUpStatus.COMPLETED;
import static io.spine.server.delivery.CatchUpStatus.FINALIZING;
import static io.spine.server.delivery.CatchUpStatus.IN_PROGRESS;
import static io.spine.server.delivery.InboxMessageStatus.DELIVERED;
import static io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob;
import static io.spine.server.delivery.given.TestInboxMessages.catchingUp;
import static io.spine.server.delivery.given.TestInboxMessages.copyWithNewId;
import static io.spine.server.delivery.given.TestInboxMessages.copyWithStatus;
import static io.spine.server.delivery.given.TestInboxMessages.delivered;
import static io.spine.server.delivery.given.TestInboxMessages.toDeliver;
import static java.util.stream.Collectors.toMap;

@DisplayName("`CatchUpStation` should")
class CatchUpStationTest extends AbstractStationTest {

    @Override
    Station newStation(DeliveryAction action) {
        return new CatchUpStation(action, new ArrayList<>());
    }

    @Test
    @DisplayName("remove live messages which correspond to a started `CatchUpJob`")
    void removeLiveMessagesIfCatchUpStarted() {
        var toDeliver = toDeliver(targetOne, type);
        var anotherToDeliver = copyWithNewId(toDeliver);
        var delivered = delivered(targetOne, type);
        var differentTarget = delivered(targetTwo, type);
        var conveyor = new Conveyor(
                ImmutableList.of(toDeliver, anotherToDeliver, delivered, differentTarget),
                new DeliveredMessagesCache()
        );

        var job = catchUpJob(type, IN_PROGRESS, currentTime(), ImmutableList.of(targetOne));
        var station = new CatchUpStation(MemoizingAction.empty(), ImmutableList.of(job));
        var result = station.process(conveyor);

        assertDeliveredCount(result, 0);

        assertContainsExactly(conveyor.removals(),
                              // expected:
                              toDeliver, anotherToDeliver);

        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              delivered, differentTarget);
    }

    @Test
    @DisplayName("deliver the messages in `CATCH_UP` status " +
            "which correspond to a started `CatchUpJob` and remove duplicates")
    void matchAndRunDeliveryAction() {
        var toCatchUp = catchingUp(targetOne, type);
        var duplicateCopy = copyWithNewId(toCatchUp);
        var anotherToCatchUp = catchingUp(targetOne, type);
        var alreadyDelivered = delivered(targetOne, type);
        var differentTarget = catchingUp(targetTwo, type);

        var initialContents =
                ImmutableList.of(toCatchUp, anotherToCatchUp, duplicateCopy,
                                 alreadyDelivered, differentTarget);
        var conveyor = new Conveyor(initialContents, new DeliveredMessagesCache());

        var job = catchUpJob(type, IN_PROGRESS, currentTime(), ImmutableList.of(targetOne));
        var station = new CatchUpStation(MemoizingAction.empty(), ImmutableList.of(job));
        var result = station.process(conveyor);

        assertDeliveredCount(result, 2);

        assertContainsExactly(conveyor.recentlyDelivered(),
                              // expected:
                              copyWithStatus(toCatchUp, DELIVERED),
                              copyWithStatus(anotherToCatchUp, DELIVERED),
                              alreadyDelivered);

        assertContainsExactly(conveyor.removals(), duplicateCopy);

        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              copyWithStatus(toCatchUp, DELIVERED),
                              copyWithStatus(anotherToCatchUp, DELIVERED),
                              alreadyDelivered,
                              differentTarget);
    }

    @Test
    @DisplayName("mark as `CATCH_UP` those live messages " +
            "which correspond to a finalizing `CatchUpJob`")
    void markLiveMessagesCatchUpIfJobFinalizing() {
        var toDeliver = toDeliver(targetOne, type);
        var anotherToDeliver = copyWithNewId(toDeliver);
        var delivered = delivered(targetOne, type);
        var differentTarget = delivered(targetTwo, type);
        var conveyor = new Conveyor(
                ImmutableList.of(toDeliver, anotherToDeliver, delivered, differentTarget),
                new DeliveredMessagesCache()
        );

        var job = catchUpJob(type, FINALIZING, currentTime(), ImmutableList.of(targetOne));
        var station = new CatchUpStation(MemoizingAction.empty(), ImmutableList.of(job));
        var result = station.process(conveyor);
        assertDeliveredCount(result, 0);

        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              copyWithStatus(toDeliver, TO_CATCH_UP),
                              copyWithStatus(anotherToDeliver, TO_CATCH_UP),
                              delivered,
                              differentTarget);
    }

    @Test
    @DisplayName("deduplicate and deliver all matching `CATCH_UP` messages " +
            "if the respective `CatchUpJob` is completed, " +
            "keeping the `CATCH_UP` messages in their storage for a bit longer")
    void deduplicateAndDeliverWhenJobCompleted() {
        var toCatchUp = catchingUp(targetOne, type);
        var moreToCatchUp = catchingUp(targetOne, type);
        var toDeliver = toDeliver(targetOne, type);
        var duplicateToCatchUp = copyWithNewId(toCatchUp);
        var duplicateToDeliver = copyWithStatus(copyWithNewId(toCatchUp), TO_DELIVER);
        var conveyor = new Conveyor(
                ImmutableList.of(toCatchUp, moreToCatchUp, toDeliver,
                                 duplicateToCatchUp, duplicateToDeliver),
                new DeliveredMessagesCache()
        );

        var job = catchUpJob(type, COMPLETED, currentTime(), ImmutableList.of(targetOne));
        var action = MemoizingAction.empty();
        var station = new CatchUpStation(action, ImmutableList.of(job));
        var result = station.process(conveyor);
        assertDeliveredCount(result, 2);

        Collection<InboxMessage> deliveredMessages = checkNotNull(action.passedMessages());
        assertContainsExactly(deliveredMessages,
                              // expected:
                              toCatchUp,
                              moreToCatchUp
        );

        var remaindersById =
                stream(conveyor.iterator()).collect(toMap(InboxMessage::getId, message -> message));
        assertThat(remaindersById).hasSize(3);

        assertKeptForLonger(toCatchUp.getId(), remaindersById);
        assertKeptForLonger(moreToCatchUp.getId(), remaindersById);
        assertNotKeptForLonger(toDeliver.getId(), remaindersById);
    }

    @Test
    @DisplayName("run the delivery action " +
            "for the messages in `CATCH_UP` status sorting them beforehand")
    void sortCatchUpMessagesBeforeCallingToAction() {
        var now = currentTime();
        var secondBefore = subtract(now, fromSeconds(1));
        var twoSecondsBefore = subtract(now, fromSeconds(2));
        var threeSecondsBefore = subtract(now, fromSeconds(3));

        var toCatchUp1 = catchingUp(targetOne, type, threeSecondsBefore);
        var toCatchUp2 = catchingUp(targetOne, type, twoSecondsBefore);
        var toCatchUp3 = catchingUp(targetOne, type, secondBefore);
        var toCatchUp4 = catchingUp(targetOne, type, now);
        var conveyor = new Conveyor(
                ImmutableList.of(toCatchUp3, toCatchUp2, toCatchUp4, toCatchUp1),
                new DeliveredMessagesCache()
        );

        var job = catchUpJob(type, IN_PROGRESS, currentTime(), ImmutableList.of(targetOne));
        var action = MemoizingAction.empty();
        var station = new CatchUpStation(action, ImmutableList.of(job));
        var result = station.process(conveyor);
        assertDeliveredCount(result, 4);

        var deliveredMessages = checkNotNull(action.passedMessages());
        assertThat(deliveredMessages).containsExactlyElementsIn(
                ImmutableList.of(toCatchUp1, toCatchUp2, toCatchUp3, toCatchUp4)
        );
    }

    @Nested
    @DisplayName("match the `InboxMessage` to a `CatchUpJob`")
    class MatchCatchUpJob {

        @Test
        @DisplayName("by a particular target ID of the `CatchUpJob`")
        void byId() {
            var messages = messagesToTargetOneOf(type);
            var job = catchUpJob(type, IN_PROGRESS, currentTime(), ImmutableList.of(targetOne));
            assertMatchesEvery(messages, job);
        }

        @Test
        @DisplayName("when the `CatchUpJob` matches all the targets of type " +
                "to which `InboxMessage` is dispatched")
        void allByType() {
            var messages = messagesToTargetOneOf(type);
            var job = catchUpJob(type, IN_PROGRESS, currentTime(), null);
            assertMatchesEvery(messages, job);
        }

        private void assertMatchesEvery(ImmutableSet<InboxMessage> messages, CatchUp job) {
            for (var message : messages) {
                assertThat(job.matches(message))
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("not match the `InboxMessage` to a `CatchUpJob`")
    class NotMatchCatchUpJob {

        @Test
        @DisplayName("when the target type of the `InboxMessage` and the `CatchUpJob` differs")
        void whenTargetTypeDiffers() {
            var messages = messagesToTargetOneOf(anotherType);
            var job = catchUpJob(type, IN_PROGRESS, currentTime(), null);
            assertMatchesNone(messages, job);
        }

        @Test
        @DisplayName("when the target ID of the `InboxMessage` " +
                "does not match the IDs enumerated in the `CatchUpJob`")
        void whenIdDoesNotMatch() {
            var messages = messagesToTargetOneOf(type);
            var job = catchUpJob(type, IN_PROGRESS, currentTime(), ImmutableSet.of(targetTwo));
            assertMatchesNone(messages, job);
        }

        private void assertMatchesNone(ImmutableSet<InboxMessage> messages, CatchUp job) {
            for (var message : messages) {
                assertThat(job.matches(message))
                        .isFalse();
            }
        }
    }

    private static ImmutableSet<InboxMessage> messagesToTargetOneOf(TypeUrl targetType) {
        return ImmutableSet.of(toDeliver(targetOne, targetType),
                               delivered(targetOne, targetType),
                               catchingUp(targetOne, targetType));
    }
}
