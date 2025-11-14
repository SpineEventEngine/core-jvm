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

import io.spine.core.UserId;
import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import io.spine.server.tuple.Pair;
import io.spine.test.client.users.command.LogInUser;
import io.spine.test.client.users.command.LogOutUser;
import io.spine.test.client.users.event.UserAccountCreated;
import io.spine.test.client.users.event.UserLoggedIn;
import io.spine.test.client.users.event.UserLoggedOut;
import io.spine.test.client.users.rejection.UserAlreadyLoggedIn;

/**
 * Performs login/logout operations and keeps the login status of the user.
 */
final class LoginProcess extends ProcessManager<UserId, LoginStatus, LoginStatus.Builder>  {

    @Assign
    Pair<UserLoggedIn, UserAccountCreated> on(LogInUser c) throws UserAlreadyLoggedIn {
        var user = c.getUser();
        var state = state();
        if (state.getUser().equals(user) && state.getLoggedIn()) {
            throw UserAlreadyLoggedIn.newBuilder()
                    .setUser(user)
                    .build();
        }
        builder().setUser(user)
                 .setLoggedIn(true)
                 .setUserId(user.getValue());
        return Pair.of(
                UserLoggedIn.newBuilder()
                        .setUser(user)
                        .build(),
                UserAccountCreated.newBuilder()
                        .setUser(user)
                        .build()
        );
    }

    @Assign
    UserLoggedOut on(LogOutUser c) {
        builder().setLoggedIn(false);
        return UserLoggedOut.newBuilder()
                .setUser(c.getUser())
                .build();
    }
}
