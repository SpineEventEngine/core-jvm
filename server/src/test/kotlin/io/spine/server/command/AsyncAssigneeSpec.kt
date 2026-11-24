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

package io.spine.server.command

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.async.given.AbstractAsyncHandlerSpec
import io.spine.server.async.given.AcmeRpcHandler
import io.spine.server.async.given.all
import io.spine.server.async.given.await
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.server.given.context.shipping.event.ShipmentReserved
import io.spine.server.given.context.shipping.rejection.CarrierUnavailable
import io.spine.server.given.context.shipping.rejection.ShippingRejections
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.system.server.event.CommandRejected
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AsyncAssignee` should")
internal class AsyncAssigneeSpec : AbstractAsyncHandlerSpec() {

    private val orderId = newOrder()
    private val handler = AcmeRpcHandler(service)

    @Test
    fun `let the posting of a command return while the handler method is running`() {
        open { addAssignee(handler) }

        post(reserveShipment { order = orderId })

        // The test thread got here, so the posting has returned. The carrier holds the call.
        service.awaitCalls()
        monitor.all<ShipmentReserved>().shouldBeEmpty()
    }

    @Test
    fun `call the handler method on a thread other than the posting one`() {
        open { addAssignee(handler) }

        post(reserveShipment { order = orderId })

        service.awaitCalls()
        service.calls().single().thread shouldNotBe Thread.currentThread()
    }

    @Test
    fun `post the event when the handler method returns`() {
        open { addAssignee(handler) }
        post(reserveShipment { order = orderId })
        service.awaitCalls()

        service.release()

        monitor.await<ShipmentReserved>().order shouldBe orderId
    }

    @Test
    fun `post the rejection thrown by the handler method`() {
        open { addAssignee(handler) }
        service.failWith {
            CarrierUnavailable.newBuilder()
                .setOrder(orderId)
                .build()
        }
        service.release()

        post(reserveShipment { order = orderId })

        monitor.await<ShippingRejections.CarrierUnavailable>().order shouldBe orderId
        monitor.await<CommandRejected>()
    }

    @Test
    @MuteLogging
    fun `report a failure of the handler method`() {
        open { addAssignee(handler) }
        service.failWith { IllegalStateException("The carrier does not respond.") }
        service.release()

        post(reserveShipment { order = orderId })

        val failed = monitor.await<HandlerFailedUnexpectedly>()
        failed.error.message shouldBe "The carrier does not respond."
        monitor.all<ShipmentReserved>().shouldBeEmpty()
    }

    @Test
    fun `handle a command under the tenant of the command`() {
        open(multiTenant = true) { addAssignee(handler) }

        post(reserveShipment { order = orderId })

        service.awaitCalls()
        service.calls().single().tenant shouldBe tenant
    }

    @Test
    fun `tell the bus that the command is passed to the asynchronous execution`() {
        open { addAssignee(handler) }
        val command = envelopeOf(reserveShipment { order = orderId })

        val outcome = handler.dispatch(command)

        outcome.sentToAsyncExecution shouldBe true
        outcome.propagatedSignal shouldBe command.messageId()
    }

    @Test
    fun `handle a command on the posting thread in tests, if no execution is set`() {
        open(onCallingThread = true) { addAssignee(handler) }
        service.release()

        post(reserveShipment { order = orderId })

        // No waiting: the event is there as soon as the posting returns.
        service.calls().single().thread shouldBe Thread.currentThread()
        monitor.all<ShipmentReserved>().single().order shouldBe orderId
    }
}
