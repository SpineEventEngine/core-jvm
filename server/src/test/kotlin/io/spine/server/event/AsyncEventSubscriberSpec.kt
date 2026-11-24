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

package io.spine.server.event

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.async.given.AbstractAsyncHandlerSpec
import io.spine.server.async.given.ShipmentArchive
import io.spine.server.async.given.await
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.event.shipmentReserved
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AsyncEventSubscriber` should")
internal class AsyncEventSubscriberSpec : AbstractAsyncHandlerSpec() {

    private val orderId = newOrder()
    private val subscriber = ShipmentArchive(service)

    @Test
    fun `let the posting of an event return while the subscribing method is running`() {
        open { addEventDispatcher(subscriber) }

        post(shipmentReserved { order = orderId })

        // The test thread got here, so the posting has returned. The archive holds the call.
        service.awaitCalls()
        subscriber.uploaded().shouldBeEmpty()
    }

    @Test
    fun `call the subscribing method on a thread other than the posting one`() {
        open { addEventDispatcher(subscriber) }

        post(shipmentReserved { order = orderId })

        service.awaitCalls()
        service.calls().single().thread shouldNotBe Thread.currentThread()
    }

    @Test
    fun `finish the subscribing method before its context is closed`() {
        open { addEventDispatcher(subscriber) }
        post(shipmentReserved { order = orderId })
        service.awaitCalls()
        service.release()

        // Closing the context waits for the subscribing method to return.
        context.close()

        subscriber.uploaded() shouldContainExactly listOf(orderId)
    }

    @Test
    @MuteLogging
    fun `report a failure of the subscribing method`() {
        open { addEventDispatcher(subscriber) }
        service.failWith { IllegalStateException("The archive does not respond.") }
        service.release()

        post(shipmentReserved { order = orderId })

        val failed = monitor.await<HandlerFailedUnexpectedly>()
        failed.error.message shouldBe "The archive does not respond."
        subscriber.uploaded().shouldBeEmpty()
    }

    @Test
    fun `handle an event under the tenant of the event`() {
        open(multiTenant = true) { addEventDispatcher(subscriber) }

        post(shipmentReserved { order = orderId })

        service.awaitCalls()
        service.calls().single().tenant shouldBe tenant
    }

    @Test
    fun `tell the bus that the event is passed to the asynchronous execution`() {
        open { addEventDispatcher(subscriber) }
        val event = envelopeOf(shipmentReserved { order = orderId })

        val outcome = subscriber.dispatch(event)

        outcome.sentToAsyncExecution shouldBe true
        outcome.propagatedSignal shouldBe event.messageId()
    }

    @Test
    fun `handle an event on the posting thread in tests, if no execution is set`() {
        open(onCallingThread = true) { addEventDispatcher(subscriber) }
        service.release()

        post(shipmentReserved { order = orderId })

        // No waiting: the papers are uploaded as soon as the posting returns.
        service.calls().single().thread shouldBe Thread.currentThread()
        subscriber.uploaded() shouldContainExactly listOf(orderId)
    }
}
