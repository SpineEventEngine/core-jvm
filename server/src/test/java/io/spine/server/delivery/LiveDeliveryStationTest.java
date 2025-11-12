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
import com.google.protobuf.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Durations.fromSeconds;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.server.delivery.InboxMessageStatus.DELIVERED;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static io.spine.server.delivery.given.TestInboxMessages.catchingUp;
import static io.spine.server.delivery.given.TestInboxMessages.copyWithNewId;
import static io.spine.server.delivery.given.TestInboxMessages.copyWithStatus;
import static io.spine.server.delivery.given.TestInboxMessages.delivered;
import static io.spine.server.delivery.given.TestInboxMessages.toDeliver;
import static java.util.stream.Collectors.toMap;

@DisplayName("`LiveDeliveryStation` should")
class LiveDeliveryStationTest extends AbstractStationTest {

    @Override
    Station newStation(DeliveryAction action) {
        return new LiveDeliveryStation(action, noWindow());
    }

    @Test
    @DisplayName("run the delivery action for all the messages in `TO_DELIVER` status")
    void runDeliveryActionForToDeliver() {
        var toDeliver = toDeliver(targetOne, type);
        var anotherToDeliver = toDeliver(targetOne, type);
        var differentTarget = toDeliver(targetTwo, type);
        var alreadyDelivered = delivered(targetOne, type);
        var toCatchUp = catchingUp(targetOne, type);

        var initialContents =
                ImmutableList.of(toDeliver, anotherToDeliver, differentTarget,
                                 alreadyDelivered, toCatchUp);
        var conveyor = new Conveyor(initialContents, new DeliveredMessagesCache());

        var action = MemoizingAction.empty();
        Station station = new LiveDeliveryStation(action, noWindow());
        var result = station.process(conveyor);

        assertDeliveredCount(result, 3);
        var passedToAction = checkNotNull(action.passedMessages());

        assertContainsExactly(passedToAction,
                              // expected:
                              toDeliver,
                              anotherToDeliver,
                              differentTarget);

        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              copyWithStatus(toDeliver, DELIVERED),
                              copyWithStatus(anotherToDeliver, DELIVERED),
                              copyWithStatus(differentTarget, DELIVERED),
                              alreadyDelivered,
                              toCatchUp);
    }

    @Test
    @DisplayName("remove duplicates prior to dispatching")
    void removeDuplicates() {
        var toDeliver = toDeliver(targetOne, type);
        var duplicate = copyWithNewId(toDeliver);
        var anotherDuplicate = copyWithNewId(toDeliver);
        var alreadyDelivered = delivered(targetOne, type);
        var duplicateOfDelivered =
                copyWithNewId(copyWithStatus(alreadyDelivered, TO_DELIVER));
        var toCatchUp = catchingUp(targetOne, type);

        var initialContents =
                ImmutableList.of(toDeliver, duplicate, anotherDuplicate,
                                 alreadyDelivered, duplicateOfDelivered, toCatchUp);
        var conveyor = new Conveyor(initialContents, new DeliveredMessagesCache());

        var action = MemoizingAction.empty();
        Station station = new LiveDeliveryStation(action, noWindow());
        var result = station.process(conveyor);

        assertDeliveredCount(result, 1);
        var passedToAction = checkNotNull(action.passedMessages());

        assertContainsExactly(passedToAction,
                              // expected:
                              toDeliver);

        assertContainsExactly(conveyor.removals(),
                              // expected:
                              duplicate,
                              anotherDuplicate,
                              duplicateOfDelivered);
        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              copyWithStatus(toDeliver, DELIVERED),
                              alreadyDelivered,
                              toCatchUp);
    }

    @Test
    @DisplayName("sort messages prior to dispatching")
    void sort() {
        var now = currentTime();
        var secondBefore = subtract(now, fromSeconds(1));
        var twoSecondsBefore = subtract(now, fromSeconds(2));
        var threeSecondsBefore = subtract(now, fromSeconds(3));

        var toDeliver1 = toDeliver(targetOne, type, threeSecondsBefore);
        var toDeliver2 = toDeliver(targetTwo, type, twoSecondsBefore);
        var toDeliver3 = toDeliver(targetOne, type, secondBefore);
        var toDeliver4 = toDeliver(targetTwo, type, now);
        var conveyor = new Conveyor(
                ImmutableList.of(toDeliver2, toDeliver3, toDeliver4, toDeliver1),
                new DeliveredMessagesCache()
        );

        var action = MemoizingAction.empty();
        Station station = new LiveDeliveryStation(action, noWindow());
        var result = station.process(conveyor);
        assertDeliveredCount(result, 4);

        var deliveredMessages = checkNotNull(action.passedMessages());
        assertThat(deliveredMessages).containsExactlyElementsIn(
                ImmutableList.of(toDeliver1, toDeliver2, toDeliver3, toDeliver4)
        );
    }

    @Test
    @DisplayName("modify the messages and keep them for longer if the deduplication window is set")
    void keepMessagesForLongerIfDeduplicationWindowSet() {
        var toDeliver = toDeliver(targetOne, type);
        var differentTarget = toDeliver(targetTwo, type);
        var alreadyDelivered = delivered(targetOne, type);
        var toCatchUp = catchingUp(targetOne, type);

        var initialContents =
                ImmutableList.of(toDeliver, differentTarget, alreadyDelivered, toCatchUp);
        var conveyor = new Conveyor(initialContents, new DeliveredMessagesCache());

        Station station = new LiveDeliveryStation(MemoizingAction.empty(), fromSeconds(100));
        var result = station.process(conveyor);

        assertDeliveredCount(result, 2);

        var contentsById =
                stream(conveyor.iterator()).collect(toMap(InboxMessage::getId,msg -> msg));
        assertKeptForLonger(toDeliver.getId(), contentsById);
        assertKeptForLonger(differentTarget.getId(), contentsById);
        assertNotKeptForLonger(alreadyDelivered.getId(), contentsById);
        assertNotKeptForLonger(toCatchUp.getId(), contentsById);
    }

    private static Duration noWindow() {
        return fromSeconds(0);
    }
}
