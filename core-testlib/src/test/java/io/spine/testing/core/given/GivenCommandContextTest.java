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

package io.spine.testing.core.given;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;
import io.spine.core.UserId;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.protobuf.util.Timestamps.add;
import static io.spine.base.Time.currentTime;
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
}
