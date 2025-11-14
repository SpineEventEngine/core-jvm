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

package io.spine.test.client.users;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.core.Subscribe;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;
import io.spine.test.client.users.event.UserLoggedIn;
import io.spine.test.client.users.event.UserLoggedOut;

import static io.spine.server.route.EventRoute.withId;

public final class ActiveUsersProjection
        extends Projection<ActiveUsersId, ActiveUsers, ActiveUsers.Builder> {

    public static final ActiveUsersId THE_ID = ActiveUsersId
            .newBuilder()
            .setValue("SingleInstance")
            .build();

    @Subscribe
    void on(UserLoggedIn event) {
        builder().setCount(builder().getCount() + 1);
    }

    @Subscribe
    void on(UserLoggedOut event) {
        builder().setCount(builder().getCount() - 1);
    }

    static final class Repository
            extends ProjectionRepository<ActiveUsersId, ActiveUsersProjection, ActiveUsers> {

        @OverridingMethodsMustInvokeSuper
        @Override
        protected void setupEventRouting(EventRouting<ActiveUsersId> routing) {
            super.setupEventRouting(routing);
            routing.replaceDefault(
                    (message, context) -> withId(THE_ID)
            );
        }
    }
}
