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
import io.spine.server.BoundedContextBuilder
import io.spine.server.async.given.AbstractAsyncHandlerSpec
import io.spine.server.async.given.InstantReservations
import io.spine.server.async.given.ShipmentPlanner
import io.spine.server.async.given.all
import io.spine.server.async.given.await
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.command.ReserveShipment
import io.spine.server.given.context.shipping.command.planShipment
import io.spine.server.given.context.shipping.event.orderPaid
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`AsyncCommander` should")
internal class AsyncCommanderSpec : AbstractAsyncHandlerSpec() {

    private val orderId = newOrder()
    private val commander = ShipmentPlanner(service)

    /**
     * Adds the commander under test, and the handler of the command it generates.
     *
     * The handler is synchronous. A context that is being closed refuses the work of
     * an asynchronous one, and some tests end while the commanding method is running.
     */
    private fun BoundedContextBuilder.shipmentPlanning() {
        addCommandDispatcher(commander)
        addAssignee(InstantReservations())
    }

    @Nested inner class
    `given a command` {

        @Test
        fun `let its posting return while the commanding method is running`() {
            open { shipmentPlanning() }

            post(planShipment { order = orderId })

            // The test thread got here, so the posting has returned.
            service.awaitCalls()
            monitor.all<ReserveShipment>().shouldBeEmpty()
        }

        @Test
        fun `post the generated command when the commanding method returns`() {
            open { shipmentPlanning() }
            post(planShipment { order = orderId })
            service.awaitCalls()

            service.release()

            monitor.await<ReserveShipment>().order shouldBe orderId
        }

        @Test
        fun `tell the bus that the command is passed to the asynchronous execution`() {
            open { shipmentPlanning() }
            val command = envelopeOf(planShipment { order = orderId })

            val outcome = commander.dispatch(command)

            outcome.sentToAsyncExecution shouldBe true
        }
    }

    @Nested inner class
    `given an event` {

        @Test
        fun `let its posting return while the commanding method is running`() {
            open { shipmentPlanning() }

            post(orderPaid { order = orderId })

            // The test thread got here, so the posting has returned.
            service.awaitCalls()
            monitor.all<ReserveShipment>().shouldBeEmpty()
        }

        @Test
        fun `post the generated command when the commanding method returns`() {
            open { shipmentPlanning() }
            post(orderPaid { order = orderId })
            service.awaitCalls()

            service.release()

            monitor.await<ReserveShipment>().order shouldBe orderId
        }

        @Test
        fun `tell the bus that the event is passed to the asynchronous execution`() {
            open { shipmentPlanning() }
            val event = envelopeOf(orderPaid { order = orderId })

            val outcome = commander.dispatchEvent(event)

            outcome.sentToAsyncExecution shouldBe true
        }
    }

    @Test
    fun `call the commanding method on a thread other than the posting one`() {
        open { shipmentPlanning() }

        post(planShipment { order = orderId })

        service.awaitCalls()
        service.calls().single().thread shouldNotBe Thread.currentThread()
    }

    @Test
    fun `handle a message under its tenant`() {
        open(multiTenant = true) { shipmentPlanning() }

        post(orderPaid { order = orderId })

        service.awaitCalls()
        service.calls().single().tenant shouldBe tenant
    }

    @Test
    @MuteLogging
    fun `report a failure of the commanding method`() {
        open { shipmentPlanning() }
        service.failWith { IllegalStateException("No carrier can be chosen.") }
        service.release()

        post(planShipment { order = orderId })

        val failed = monitor.await<HandlerFailedUnexpectedly>()
        failed.error.message shouldBe "No carrier can be chosen."
        monitor.all<ReserveShipment>().shouldBeEmpty()
    }

    @Test
    fun `handle a message on the posting thread in tests, if no execution is set`() {
        open(onCallingThread = true) { shipmentPlanning() }
        service.release()

        post(planShipment { order = orderId })

        // No waiting: the command is there as soon as the posting returns.
        service.calls().single().thread shouldBe Thread.currentThread()
        monitor.all<ReserveShipment>().single().order shouldBe orderId
    }
}
