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
package io.spine.testing.core.given;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;
import io.spine.core.CommandContext.Schedule;
import io.spine.core.UserId;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.protobuf.util.Timestamps.add;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.Durations2.hours;
import static io.spine.protobuf.Durations2.minutes;
import static io.spine.testing.core.given.GivenUserId.newUuid;
import static io.spine.validation.Validate.check;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DisplayName("`GivenCommandContext` should")
class GivenCommandContextTest extends UtilityClassTest<GivenCommandContext> {

    GivenCommandContextTest() {
        super(GivenCommandContext.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        super.configure(tester);
        tester.setDefault(UserId.class, GivenUserId.generated())
              .setDefault(Timestamp.class, Time.currentTime());
    }

    @Test
    @DisplayName("create `CommandContext` with random actor")
    void createWithRandomActor() {
        var first = GivenCommandContext.withRandomActor();
        var second = GivenCommandContext.withRandomActor();

        check(first);
        check(second);

        var firstActorContext = first.actorContext();
        var secondActorContext = second.actorContext();
        assertNotEquals(firstActorContext.getActor(), secondActorContext.getActor());
    }

    @Test
    @DisplayName("create `CommandContext` with actor and time")
    void createWithActorAndTime() {
        var actorId = newUuid();
        var when = add(currentTime(), minutes(100));

        var context = GivenCommandContext.withActorAndTime(actorId, when);
        check(context);

        var actualActorContext = context.getActorContext();

        assertEquals(actorId, actualActorContext.getActor());
        assertEquals(when, actualActorContext.getTimestamp());
    }

    @Test
    @DisplayName("create `CommandContext` with scheduled delay")
    void createWithScheduledDelay() {
        var delay = hours(42);
        var expectedSchedule = Schedule.newBuilder()
                .setDelay(delay)
                .build();

        var context = GivenCommandContext.withScheduledDelayOf(delay);
        check(context);

        var actualSchedule = context.getSchedule();
        assertEquals(expectedSchedule, actualSchedule);
    }
}
