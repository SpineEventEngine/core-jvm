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

import io.kotest.matchers.shouldBe
import io.spine.client.Subscriptions
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.test.event.ProjectCreated
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Stand`, releasing a subscription, should")
internal class StandReleaseSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val stand = Stand.newBuilder().build()
    private val subscription =
        Subscriptions.from(requests.topic().allOf(ProjectCreated::class.java))
    private val callback = SubscriptionCallback { }

    @BeforeEach
    fun subscribe() {
        stand.subscribe(subscription, setOf(0))
    }

    @Test
    fun `cancel the subscription activated with the given callback`() {
        stand.activate(subscription, callback, noOpObserver())

        stand.release(subscription, callback) shouldBe true

        stand.hasSubscription(subscription) shouldBe false
    }

    @Test
    fun `keep the subscription activated again with another callback`() {
        stand.activate(subscription, callback, noOpObserver())
        stand.activate(subscription, SubscriptionCallback { }, noOpObserver())

        stand.release(subscription, callback) shouldBe false

        stand.hasSubscription(subscription) shouldBe true
    }

    @Test
    fun `keep the subscription that is not activated`() {
        stand.release(subscription, callback) shouldBe false

        stand.hasSubscription(subscription) shouldBe true
    }

    @Test
    fun `ignore a subscription it does not have`() {
        val other = Subscriptions.from(requests.topic().allOf(ProjectCreated::class.java))

        stand.release(other, callback) shouldBe false

        stand.hasSubscription(subscription) shouldBe true
    }
}
