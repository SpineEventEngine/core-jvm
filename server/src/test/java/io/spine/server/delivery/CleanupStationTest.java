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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.protobuf.util.Durations.fromSeconds;
import static com.google.protobuf.util.Timestamps.add;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.server.delivery.given.TestInboxMessages.catchingUp;
import static io.spine.server.delivery.given.TestInboxMessages.delivered;
import static io.spine.server.delivery.given.TestInboxMessages.toDeliver;

@DisplayName("`CleanupStation` should")
class CleanupStationTest extends AbstractStationTest {

    @Override
    Station newStation(DeliveryAction action) {
        return new CleanupStation();
    }

    @Test
    @DisplayName("remove the messages in `DELIVERED` status")
    void removeDeliveredMessages() {
        var delivered = delivered(targetOne, type);
        var deliveredToAnotherTarget = delivered(targetTwo, type);
        var catchingUp = catchingUp(targetTwo, type);
        var toDeliver = toDeliver(targetOne, type);
        var conveyor = new Conveyor(
                ImmutableList.of(delivered, deliveredToAnotherTarget, catchingUp, toDeliver),
                new DeliveredMessagesCache()
        );

        Station station = new CleanupStation();
        var result = station.process(conveyor);

        assertDeliveredCount(result, 0);

        assertContainsExactly(conveyor.removals(),
                              // expected:
                              delivered, deliveredToAnotherTarget);

        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              catchingUp, toDeliver);
    }

    @Test
    @DisplayName("keep the messages in `DELIVERED` status if they have `keep_until` in future" +
            "and remove `DELIVERED` messages if their `keep_until` is in the past")
    void considerKeepUntilWhenRemoving() {
        var deliveredKeepTillFuture = keepUntil(
                delivered(targetOne, type), add(currentTime(), fromSeconds(10))
        );
        var deliveredKeepUntilPastTime = keepUntil(
                delivered(targetTwo, type), subtract(currentTime(), fromSeconds(5))
        );
        var conveyor = new Conveyor(
                ImmutableList.of(deliveredKeepTillFuture, deliveredKeepUntilPastTime),
                new DeliveredMessagesCache()
        );

        Station station = new CleanupStation();
        var result = station.process(conveyor);
        assertDeliveredCount(result, 0);

        assertContainsExactly(conveyor.removals(),
                              // expected:
                              deliveredKeepUntilPastTime);

        assertContainsExactly(conveyor.iterator(),
                              // expected:
                              deliveredKeepTillFuture);
    }

    private static InboxMessage keepUntil(InboxMessage message, Timestamp tillWhen) {
        return message.toBuilder()
                        .setKeepUntil(tillWhen)
                        .build();
    }
}
