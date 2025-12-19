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

package io.spine.testing.server.blackbox;

import io.spine.annotation.VisibleForTesting;
import io.spine.base.Time;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.time.ZoneId;
import io.spine.time.ZoneIds;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.validation.Validate.check;

/**
 * A factory of test actor request factories.
 */
@VisibleForTesting
final class Actor {

    private static final Actor defaultActor = from(
            UserId.newBuilder()
                  .setValue(BlackBox.class.getName())
                  .build()
    );

    private final UserId id;
    private final ZoneId zoneId;

    private Actor(UserId id, @Nullable ZoneId zoneId) {
        this.id = id;
        this.zoneId = zoneId == null
                ? ZoneIds.of(Time.currentTimeZone())
                : zoneId;
    }

    /** Obtains the default actor. */
    static Actor defaultActor() {
        return defaultActor;
    }

    /** Creates a new actor with the given actor ID and the default time zone. */
    static Actor from(UserId userId) {
        checkUser(userId);
        return new Actor(userId, null);
    }

    /** Creates a copy of this actor with the passed time zone. */
    Actor in(ZoneId zone) {
        checkZone(zone);
        return new Actor(this.id, zone);
    }

    /** Creates a copy of this actor with the passed user ID. */
    Actor withId(UserId id) {
        checkUser(id);
        return new Actor(id, this.zoneId);
    }

    private static void checkUser(UserId userId) {
        checkNotNull(userId);
        check(userId);
    }

    private static void checkZone(ZoneId zone) {
        checkNotNull(zone);
        check(zone);
    }

    /** Creates a new factory for requests of the single tenant. */
    TestActorRequestFactory requests() {
        return new TestActorRequestFactory(null, id, zoneId);
    }

    /** Creates a new factory for requests of the given tenant. */
    TestActorRequestFactory requestsFor(TenantId tenant) {
        checkNotNull(tenant);
        check(tenant);
        return new TestActorRequestFactory(tenant, id, zoneId);
    }
}
