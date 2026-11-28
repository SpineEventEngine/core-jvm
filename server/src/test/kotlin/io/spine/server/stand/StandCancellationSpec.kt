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

package io.spine.server.stand

import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import io.spine.client.SubscriptionUpdate
import io.spine.client.Subscriptions
import io.spine.core.Response
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.test.event.ProjectCreated
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Stand`, cancelling a subscription, should")
internal class StandCancellationSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val stand = Stand.newBuilder().build()
    private val subscription =
        Subscriptions.from(requests.topic().allOf(ProjectCreated::class.java))
    private val callback = CountingCallback()

    @BeforeEach
    fun subscribe() {
        stand.subscribe(subscription, setOf(0))
    }

    @Test
    fun `tell the callback it was activated with, and acknowledge the cancellation`() {
        stand.activate(subscription, callback, noOpObserver())
        val response = memoizingObserver<Response>()

        stand.cancel(subscription, response)

        callback.cancellations shouldBe 1
        response.responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
        response.isCompleted shouldBe true
    }

    @Test
    fun `tell each callback once, when it serves the subscription no more`() {
        val latest = CountingCallback()
        stand.activate(subscription, callback, noOpObserver())

        stand.activate(subscription, latest, noOpObserver())

        callback.cancellations shouldBe 1
        latest.cancellations shouldBe 0

        stand.cancel(subscription, noOpObserver())

        callback.cancellations shouldBe 1
        latest.cancellations shouldBe 1
    }

    @Test
    fun `not tell the callback when the subscription is released`() {
        stand.activate(subscription, callback, noOpObserver())

        stand.release(subscription, callback) shouldBe true

        callback.cancellations shouldBe 0
    }

    @Test
    @MuteLogging
    fun `cancel the subscription and acknowledge it, even if the callback fails`() {
        val failing = object : SubscriptionCallback {
            override fun accept(update: SubscriptionUpdate) = Unit
            override fun onCancelled() {
                error("The callback fails on purpose.")
            }
        }
        stand.activate(subscription, failing, noOpObserver())
        val response = memoizingObserver<Response>()

        stand.cancel(subscription, response)

        stand.hasSubscription(subscription) shouldBe false
        response.responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
    }

    @Test
    fun `cancel a subscription that is not activated, and acknowledge it`() {
        val response = memoizingObserver<Response>()

        stand.cancel(subscription, response)

        stand.hasSubscription(subscription) shouldBe false
        response.responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
    }
}

/**
 * Counts the cancellations it is told about.
 */
private class CountingCallback : SubscriptionCallback {

    var cancellations = 0
        private set

    override fun accept(update: SubscriptionUpdate) = Unit

    override fun onCancelled() {
        cancellations++
    }
}
