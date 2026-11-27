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

package io.spine.server.model

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.core.MessageId
import io.spine.core.Signal
import io.spine.core.grandOriginOrNull
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.Application
import io.spine.server.BoundedContext
import io.spine.server.application
import io.spine.server.async.given.OrderShippingProcess
import io.spine.server.async.given.newOrder
import io.spine.server.command.AbstractAssignee
import io.spine.server.command.AbstractCommander
import io.spine.server.command.Assign
import io.spine.server.command.Command
import io.spine.server.given.context.shipping.OrderId
import io.spine.server.given.context.shipping.command.PlanShipment
import io.spine.server.given.context.shipping.command.ReserveShipment
import io.spine.server.given.context.shipping.command.planShipment
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.server.given.context.shipping.command.shipOrder
import io.spine.server.given.context.shipping.event.OrderPaid
import io.spine.server.given.context.shipping.event.ShipmentReserved
import io.spine.server.given.context.shipping.event.orderPaid
import io.spine.server.given.context.shipping.event.shipmentReserved
import io.spine.server.given.context.shipping.rejection.CarrierUnavailable
import io.spine.server.given.context.shipping.rejection.ShippingRejections
import io.spine.server.serverEnvironment
import io.spine.server.singleTenantContext
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.server.TestEventFactory
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks that a command emitted by a `@Command` method keeps the ancestry of
 * the handled signal.
 */
@DisplayName("`CommandEmitter` should")
internal class CommandEmitterSpec {

    private val orderId = newOrder()
    private val requests = TestActorRequestFactory(javaClass)
    private val carrier = Carrier()
    private val commands = mutableListOf<CommandEnvelope>()
    private val events = mutableListOf<EventEnvelope>()
    private val posted = mutableSetOf<MessageId>()
    private lateinit var shipping: Application
    private lateinit var context: BoundedContext

    @BeforeEach
    fun openContext() {
        shipping = application {
            serverEnvironment = serverEnvironment {}
            add(singleTenantContext("Shipping") {
                addCommandDispatcher(Planner())
                addAssignee(carrier)
                add(OrderShippingProcess::class.java)
                addCommandListener { commands.add(it) }
                addEventListener { events.add(it) }
            })
        }
        context = shipping.contexts().single()
    }

    @AfterEach
    fun closeContext() {
        shipping.close()
    }

    @Test
    fun `make the handled command the origin of a command it emits`() {
        val planning = post(planShipment { order = orderId })

        val reservation = emitted<ReserveShipment>()

        reservation.context().origin shouldBe planning.asMessageOrigin()
        reservation.rootMessage() shouldBe planning.messageId()
    }

    @Test
    fun `make the handled event the origin of a command it emits`() {
        val paid = post(orderPaid { order = orderId })

        val reservation = emitted<ReserveShipment>()

        reservation.context().origin shouldBe paid.asMessageOrigin()
        reservation.rootMessage() shouldBe paid.rootMessage()
    }

    @Test
    fun `make the handled rejection the origin of a command it emits`() {
        carrier.beBusyOnceFor(orderId)
        val firstTry = post(reserveShipment { order = orderId })

        val rejection = published<ShippingRejections.CarrierUnavailable>()
        val secondTry = emitted<ReserveShipment>()

        secondTry.context().origin shouldBe rejection.asMessageOrigin()
        secondTry.rootMessage() shouldBe firstTry.messageId()
    }

    @Test
    fun `give the origin to a command that a process manager emits`() {
        val shipping = post(shipOrder { order = orderId })

        val reservation = emitted<ReserveShipment>()

        reservation.context().origin shouldBe shipping.asMessageOrigin()
        reservation.rootMessage() shouldBe shipping.messageId()
    }

    @Test
    fun `let the events caused by an emitted command lead back to the root signal`() {
        val paid = post(orderPaid { order = orderId })

        val reservation = emitted<ReserveShipment>()
        val reserved = published<ShipmentReserved>()

        ancestryOf(reserved) shouldContainExactly listOf(
            reservation.messageId(),
            paid.messageId(),
            paid.rootMessage()
        )
        reserved.rootMessage() shouldBe paid.rootMessage()
    }

    /**
     * Posts a command with the given message on behalf of the test.
     */
    private fun post(message: CommandMessage) =
        requests.createCommand(message).also {
            posted.add(it.messageId())
            context.commandBus().post(it, noOpObserver())
        }

    /**
     * Posts an event with the given message, produced in response to a command
     * that the test does not post.
     */
    private fun post(message: EventMessage) =
        TestEventFactory.newInstance(requests).createEvent(message).also {
            context.eventBus().post(it)
        }

    /**
     * Obtains the only command of the type [M] that a handler, not the test, posted.
     */
    private inline fun <reified M : CommandMessage> emitted() =
        commands.map { it.outerObject() }
            .single { it.enclosedMessage() is M && it.messageId() !in posted }

    /**
     * Obtains the only event of the type [M] posted to the Bounded Context.
     */
    private inline fun <reified M : EventMessage> published() =
        events.map { it.outerObject() }
            .single { it.enclosedMessage() is M }
}

/**
 * Obtains the IDs of the signals that the given one descends from, starting with its parent.
 */
private fun ancestryOf(signal: Signal<*, *, *>): List<MessageId> =
    generateSequence(signal.origin().getOrNull()) { it.grandOriginOrNull }
        .map { it.message }
        .toList()

/**
 * Asks to reserve a shipment when a shipment is planned, when an order is paid,
 * and once again when the carrier is unavailable.
 */
private class Planner : AbstractCommander() {

    @Command
    internal fun on(command: PlanShipment): ReserveShipment =
        reserveShipment { order = command.order }

    @Command
    internal fun on(event: OrderPaid): ReserveShipment =
        reserveShipment { order = event.order }

    @Command
    internal fun on(rejection: ShippingRejections.CarrierUnavailable): ReserveShipment =
        reserveShipment { order = rejection.order }
}

/**
 * Reserves shipments at once, unless it is told to be busy for an order.
 */
private class Carrier : AbstractAssignee() {

    private val busyFor = mutableSetOf<OrderId>()

    /**
     * Makes this carrier reject the next reservation for the given order.
     */
    fun beBusyOnceFor(order: OrderId) {
        busyFor.add(order)
    }

    @Assign
    @Throws(CarrierUnavailable::class)
    internal fun reserve(command: ReserveShipment): ShipmentReserved {
        if (busyFor.remove(command.order)) {
            throw CarrierUnavailable.newBuilder()
                .setOrder(command.order)
                .build()
        }
        return shipmentReserved { order = command.order }
    }
}
