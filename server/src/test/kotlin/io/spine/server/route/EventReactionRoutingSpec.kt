/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.route

import io.spine.server.given.context.users.RSessionId
import io.spine.server.given.context.users.SessionProjection
import io.spine.server.given.context.users.createUsersContext
import io.spine.server.given.context.users.event.rUserSignedIn
import io.spine.server.given.context.users.rSession
import io.spine.testing.server.blackbox.assertEntity
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.server.blackbox.BlackBox
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest

@DisplayName("Event routing for a reaction event should")
internal class EventReactionRoutingSpec {

    /**
     * Verifies that the event generated as a reaction to another event is routed only after
     * the origin event has updated the projection the reaction routing depends on.
     *
     * The origin event `RUserSignedIn` is dispatched both to the `SessionProjection` (which
     * records `userId`) and to the `UserAggregate` (which reacts with `RUserConsentRequested`).
     * The reaction is routed by querying the session by its `userId`, so it can only reach the
     * right projection once the origin event has been delivered to that projection. The delivery
     * pipeline now dispatches an event to its subscribers before its reactors, making this
     * ordering deterministic. See the GitHub issue link for details.
     *
     * The test is kept `@RepeatedTest` as a regression guard, since it historically failed
     * intermittently depending on the non-deterministic dispatch order.
     */
    @RepeatedTest(50)
    @DisplayName("only occur after the origin event has been already dispatched")
    fun occurAfterOriginDispatched() {
        val userId = GivenUserId.generated()
        val sessionId = RSessionId.generate()

        BlackBox.from(createUsersContext()).use { context ->

            context.receivesEvent(rUserSignedIn {
                user = userId
                session = sessionId
            })

            val expected = rSession {
                id = sessionId
                this@rSession.userId = userId
                // Check that the event `RUserConsentRequested` was dispatched to
                // the corresponding `SessionProjection`.
                userConsentRequested = true
            }

            context.assertEntity<SessionProjection, _>(sessionId)
                .hasStateThat()
                .comparingExpectedFieldsOnly()
                .isEqualTo(expected)
        }
    }
}
