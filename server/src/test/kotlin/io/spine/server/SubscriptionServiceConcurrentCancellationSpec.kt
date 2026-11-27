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
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.core.Response
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.given.SubscriptionServer
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of a subscription that another
 * request cancels while the service serves it.
 *
 * Each spec makes the second of the two contexts that hold the subscription lose it
 * part-way: an observer cancels the subscription there when its stream completes in
 * the first context. The specs call the service directly, so the order of the steps
 * does not depend on threads.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, when another request cancels a subscription, should")
internal class SubscriptionServiceConcurrentCancellationSpec {

    private val server = SubscriptionServer()
    private val subscription =
        server.subscribe(UserLoggedIn::class.java, CTaskCreated::class.java)

    @AfterEach
    fun closeServer() {
        server.close()
    }

    @Test
    fun `complete the stream of an activation that the cancellation interrupts`() {
        // The later activation ends the earlier one in the first context, so the earlier
        // stream completes, and its observer cancels the subscription in the second context
        // before the later activation reaches it.
        server.directService.activate(subscription, cancellingInSecondContext())
        val later = memoizingObserver<SubscriptionUpdate>()

        server.directService.activate(subscription, later)

        later.error.shouldBeNull()
        later.isCompleted shouldBe true
        server.holds(subscription) shouldBe false
        nothingRecorded()
    }

    @Test
    fun `answer a cancellation that finds the subscription cancelled in a context`() {
        server.directService.activate(subscription, cancellingInSecondContext())
        val response = memoizingObserver<Response>()

        server.directService.cancel(subscription, response)

        response.error.shouldBeNull()
        response.responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
        response.isCompleted shouldBe true
        server.holds(subscription) shouldBe false
        nothingRecorded()
    }

    /**
     * Creates an observer of the updates of the subscription that cancels it in the second
     * context when its stream completes.
     */
    private fun cancellingInSecondContext(): MemoizingObserver<SubscriptionUpdate> {
        val second = listOf(server.tasks, server.users).sorted().last()
        return CancellingObserver(second, subscription)
    }

    private fun nothingRecorded() {
        server.usersMonitor.events.shouldBeEmpty()
        server.tasksMonitor.events.shouldBeEmpty()
    }
}

/**
 * Cancels the subscription in the given context when the stream completes.
 */
private class CancellingObserver(
    private val context: BoundedContext,
    private val subscription: Subscription
) : MemoizingObserver<SubscriptionUpdate>() {

    override fun onCompleted() {
        super.onCompleted()
        context.stand().cancel(subscription, noOpObserver())
    }
}
