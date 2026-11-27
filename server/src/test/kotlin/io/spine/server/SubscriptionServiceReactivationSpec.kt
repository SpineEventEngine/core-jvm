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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.client.SubscriptionUpdate
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.given.SubscriptionServer
import io.spine.server.given.errorCase
import io.spine.server.given.waitFor
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of a subscription that is activated
 * by several calls, one after another.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, activating a subscription again, should")
internal class SubscriptionServiceReactivationSpec {

    private val server = SubscriptionServer()

    @AfterEach
    fun closeServer() {
        server.close()
    }

    @Test
    fun `complete the stream of the earlier activation`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val first = server.activate(subscription)

        val second = server.activate(subscription)

        waitFor("the completion of the first stream") { first.completed }
        first.error.shouldBeNull()
        second.completed shouldBe false
    }

    @Test
    fun `pass the updates only to the latest activation, and let a cancellation end it`() {
        val subscription = server.subscribe(UserLoggedIn::class.java)
        val first = server.activate(subscription)
        val second = server.activate(subscription)

        server.logIn()
        waitFor("an update of the second stream") { second.updates.isNotEmpty() }
        server.cancel(subscription).status.hasOk() shouldBe true

        waitFor("the completion of the second stream") { second.completed }
        first.updates.shouldBeEmpty()
    }

    /**
     * Makes the second of the two contexts that hold the subscription lose it while
     * the later activation runs. The later activation then succeeds in the first context,
     * which ends the earlier activation, and fails in the second.
     */
    @Test
    fun `fail the activation that one of the contexts refuses, and cancel it in every context`() {
        val subscription = server.subscribe(UserLoggedIn::class.java, CTaskCreated::class.java)
        val secondContext = listOf(server.tasks, server.users).sorted().last()
        val earlier = object : MemoizingObserver<SubscriptionUpdate>() {
            override fun onCompleted() {
                super.onCompleted()
                secondContext.stand().cancel(subscription, noOpObserver())
            }
        }
        server.directService.activate(subscription, earlier)
        val later = memoizingObserver<SubscriptionUpdate>()

        server.directService.activate(subscription, later)

        earlier.isCompleted shouldBe true
        later.error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR
        later.isCompleted shouldBe false
        server.holds(subscription) shouldBe false
    }
}
