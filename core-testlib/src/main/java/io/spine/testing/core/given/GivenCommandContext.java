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

import com.google.protobuf.Timestamp;
import io.spine.core.ActorContext;
import io.spine.core.CommandContext;
import io.spine.core.UserId;

import static io.spine.base.Time.currentTime;

/**
 * Factory methods to create {@code CommandContext} instances for test purposes.
 */
public final class GivenCommandContext {

    /** Prevent instantiation of this utility class. */
    private GivenCommandContext() {
    }

    /**
     * Creates a new {@link CommandContext} instance with a randomly
     * generated {@linkplain UserId actor} and current timestamp as a creation date.
     *
     * @return a new {@code CommandContext} instance
     */
    public static CommandContext withRandomActor() {
        var userId = GivenUserId.newUuid();
        var now = currentTime();
        return withActorAndTime(userId, now);
    }

    /**
     * Creates a new {@link CommandContext} instance based upon given actor and creation date.
     *
     * @return a new {@code CommandContext} instance
     */
    public static CommandContext withActorAndTime(UserId actor, Timestamp when) {
        var tenantId = GivenTenantId.generate();
        var actorContext = ActorContext.newBuilder()
                .setActor(actor)
                .setTimestamp(when)
                .setTenantId(tenantId)
                .build();
        var result = CommandContext.newBuilder()
                .setActorContext(actorContext)
                .build();
        return result;
    }
}
