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

package io.spine.server.delivery;

import com.google.common.testing.EqualsTester;
import io.spine.base.Time;
import io.spine.core.Command;
import io.spine.test.delivery.DCreateTask;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.protobuf.util.Durations.fromSeconds;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.server.delivery.given.TestInboxMessages.toDeliver;

@DisplayName("`DispatchingId` should")
class DispatchingIdTest {

    private final TestActorRequestFactory factory =
            new TestActorRequestFactory(DispatchingIdTest.class);

    @Test
    @DisplayName("support equality")
    void supportEquality() {
        var now = Time.currentTime();
        var inPast = subtract(now, fromSeconds(1));
        var commandA = command("Target A");
        var commandB = command("Target B");

        var messageOne = new DispatchingId(toDeliver(commandA, now));
        var anotherMessageOne = new DispatchingId(toDeliver(commandA, now));
        var messageOneFromPast = new DispatchingId(toDeliver(commandA, inPast));

        var messageTwo = new DispatchingId(toDeliver(commandB, now));
        var messageTwoFromPast = new DispatchingId(toDeliver(commandB, inPast));

        new EqualsTester().addEqualityGroup(messageOne, anotherMessageOne, messageOneFromPast)
                          .addEqualityGroup(messageTwo, messageTwoFromPast)
                          .testEquals();
    }

    private Command command(String taskId) {
        return factory.createCommand(DCreateTask.newBuilder()
                                                .setId(taskId)
                                                .build());
    }
}
