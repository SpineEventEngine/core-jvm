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

package io.spine.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.server.given.SubscriptionServer
import io.spine.server.given.waitFor
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of the stream of updates that
 * a cancellation ends.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, cancelling an activated subscription, should")
internal class SubscriptionServiceCancelSpec {

    private val server = SubscriptionServer()

    @AfterEach
    fun closeServer() {
        server.close()
    }

    @Test
    fun `complete the stream of updates`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val stream = server.activate(subscription)

        server.cancel(subscription).status.hasOk() shouldBe true

        waitFor("the completion of the stream") { stream.completed }
        stream.error.shouldBeNull()
        server.holds(subscription) shouldBe false
    }

    @Test
    fun `complete the stream once, if several contexts hold the subscription`() {
        val subscription = server.subscribe(UserLoggedIn::class.java, CTaskCreated::class.java)
        val stream = server.activate(subscription)

        server.cancel(subscription).status.hasOk() shouldBe true

        waitFor("the completion of the stream") { stream.completed }
        stream.error.shouldBeNull()
        server.usersMonitor.events.shouldBeEmpty()
        server.tasksMonitor.events.shouldBeEmpty()
    }

    @Test
    fun `send no updates after the stream completes`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val stream = server.activate(subscription)
        val witness = server.activate(server.subscribe(UserLoggedIn::class.java))
        server.cancel(subscription)
        waitFor("the completion of the stream") { stream.completed }

        server.logIn()

        waitFor("the update of another subscription") { witness.updates.isNotEmpty() }
        stream.updates.shouldBeEmpty()
    }
}
