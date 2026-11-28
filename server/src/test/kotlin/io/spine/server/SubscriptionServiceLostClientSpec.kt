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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.server.given.FrozenCall
import io.spine.server.given.SubscriptionServer
import io.spine.server.given.waitFor
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of a subscription whose client
 * goes away without cancelling it.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, when the call that activated a subscription ends, should")
internal class SubscriptionServiceLostClientSpec {

    private val server = SubscriptionServer()

    @AfterEach
    fun closeServer() {
        server.close()
    }

    @Test
    fun `cancel the subscription when the client cancels the call`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val stream = server.activate(subscription)

        stream.cancel()

        waitFor("the cancellation of the subscription") { !server.holds(subscription) }
    }

    @Test
    fun `cancel the subscription in every context that holds it when the client goes away`() {
        val subscription = server.subscribe(UserLoggedIn::class.java, CTaskCreated::class.java)
        server.activate(subscription)
        server.users.stand().hasSubscription(subscription) shouldBe true
        server.tasks.stand().hasSubscription(subscription) shouldBe true

        server.channel.shutdownNow()

        waitFor("the cancellation of the subscription") { !server.holds(subscription) }
        server.usersMonitor.events.shouldBeEmpty()
        server.tasksMonitor.events.shouldBeEmpty()
    }

    @Test
    fun `keep the subscription that another call activated again`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val first = server.activate(subscription)
        val second = server.activate(subscription)

        first.cancel()
        server.logIn()

        waitFor("an update of the second call") { second.updates.isNotEmpty() }
        second.updates shouldHaveSize 1
        server.holds(subscription) shouldBe true
        server.usersMonitor.events.shouldBeEmpty()
        server.tasksMonitor.events.shouldBeEmpty()
    }

    @Test
    fun `cancel the subscription with the call that activated it again`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        server.activate(subscription)
        val second = server.activate(subscription)

        second.cancel()

        waitFor("the cancellation of the subscription") { !server.holds(subscription) }
    }

    @Test
    fun `activate the subscription, if the cancellation handler of the call cannot be set`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val call = FrozenCall()

        server.directService.activate(subscription, call)
        server.logIn()

        call.updates shouldHaveSize 1
        server.holds(subscription) shouldBe true
    }
}
