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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.async.AsyncExecution
import io.spine.server.async.given.AbstractAsyncHandlerSpec
import io.spine.server.async.given.ExternalShipmentVerifier
import io.spine.server.async.given.ShipmentVerifier
import io.spine.server.async.given.all
import io.spine.server.async.given.await
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.event.ShipmentVerified
import io.spine.server.given.context.shipping.event.shipmentReserved
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AsyncEventReactor` should")
internal class AsyncEventReactorSpec : AbstractAsyncHandlerSpec() {

    private val orderId = newOrder()
    private val reactor = ShipmentVerifier(service)

    @Test
    fun `let the posting of an event return while the reacting method is running`() {
        open { addEventDispatcher(reactor) }

        post(shipmentReserved { order = orderId })

        // The test thread got here, so the posting has returned. The carrier holds the call.
        service.awaitCalls()
        monitor.all<ShipmentVerified>().shouldBeEmpty()
    }

    @Test
    fun `call the reacting method on a thread other than the posting one`() {
        open { addEventDispatcher(reactor) }

        post(shipmentReserved { order = orderId })

        service.awaitCalls()
        service.calls().single().thread shouldNotBe Thread.currentThread()
    }

    @Test
    fun `post the event when the reacting method returns`() {
        open { addEventDispatcher(reactor) }
        post(shipmentReserved { order = orderId })
        service.awaitCalls()

        service.release()

        monitor.await<ShipmentVerified>().order shouldBe orderId
    }

    @Test
    @MuteLogging
    fun `report a failure of the reacting method`() {
        open { addEventDispatcher(reactor) }
        service.failWith { IllegalStateException("The carrier does not respond.") }
        service.release()

        post(shipmentReserved { order = orderId })

        val failed = monitor.await<HandlerFailedUnexpectedly>()
        failed.error.message shouldBe "The carrier does not respond."
        monitor.all<ShipmentVerified>().shouldBeEmpty()
    }

    @Test
    fun `handle an event under the tenant of the event`() {
        open(multiTenant = true) { addEventDispatcher(reactor) }

        post(shipmentReserved { order = orderId })

        service.awaitCalls()
        service.calls().single().tenant shouldBe tenant
    }

    @Test
    fun `tell the bus that the event is passed to the asynchronous execution`() {
        open { addEventDispatcher(reactor) }
        val event = envelopeOf(shipmentReserved { order = orderId })

        val outcome = reactor.dispatch(event)

        outcome.sentToAsyncExecution shouldBe true
        outcome.propagatedSignal shouldBe event.messageId()
    }

    @Test
    fun `handle an event on the posting thread in tests, if no execution is set`() {
        open(onCallingThread = true) { addEventDispatcher(reactor) }
        service.release()

        post(shipmentReserved { order = orderId })

        // No waiting: the event is there as soon as the posting returns.
        service.calls().single().thread shouldBe Thread.currentThread()
        monitor.all<ShipmentVerified>().single().order shouldBe orderId
    }

    @Test
    fun `react to an event of another context outside the thread that brings the event`() {
        val carriers = assumingTests("Carriers")
        val shipping = assumingTests("Shipping")
            .setAsyncExecution(AsyncExecution.newBuilder().build())
            .addEventDispatcher(ExternalShipmentVerifier(service))
        monitor.watch(shipping)
        testApplication {
            add(carriers)
            add(shipping)
        }.use { application ->
            val source = application.contexts().single { it.name().value == "Carriers" }
            val reserved = TestEventFactory.newInstance(javaClass)
                .createEvent(shipmentReserved { order = orderId })

            source.eventBus().post(reserved)

            // The posting has returned, and the reactor of the other context holds the call.
            service.awaitCalls()
            service.calls().single().thread shouldNotBe Thread.currentThread()
            monitor.all<ShipmentVerified>().shouldBeEmpty()

            service.release()
            monitor.await<ShipmentVerified>().order shouldBe orderId
        }
    }
}
