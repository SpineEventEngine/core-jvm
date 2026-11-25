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

package io.spine.server.async.given

import io.spine.base.Identifier
import io.spine.core.External
import io.spine.core.Subscribe
import io.spine.server.command.AbstractAssignee
import io.spine.server.command.Assign
import io.spine.server.command.AsyncAssignee
import io.spine.server.command.AsyncCommander
import io.spine.server.command.Command
import io.spine.server.event.AsyncEventReactor
import io.spine.server.event.AsyncEventSubscriber
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.given.context.shipping.OrderId
import io.spine.server.given.context.shipping.OrderShipping
import io.spine.server.given.context.shipping.command.PlanShipment
import io.spine.server.given.context.shipping.command.ReserveShipment
import io.spine.server.given.context.shipping.command.ShipOrder
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.server.given.context.shipping.event.OrderPaid
import io.spine.server.given.context.shipping.event.ShipmentReserved
import io.spine.server.given.context.shipping.event.ShipmentVerified
import io.spine.server.given.context.shipping.event.shipmentReserved
import io.spine.server.given.context.shipping.event.shipmentVerified
import io.spine.server.given.context.shipping.orderId
import io.spine.server.given.context.shipping.rejection.CarrierUnavailable
import io.spine.server.procman.ProcessManager
import io.spine.server.route.Route
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Creates the ID of a new order.
 */
internal fun newOrder(): OrderId = orderId { uuid = Identifier.newUuid() }

/**
 * Reserves a shipment with the Acme carrier, which takes its time to respond.
 *
 * @param acme The stub of the API of the carrier.
 */
internal class AcmeRpcHandler(private val acme: RemoteService) : AsyncAssignee() {

    @Assign
    @Throws(CarrierUnavailable::class)
    internal fun reserve(command: ReserveShipment): ShipmentReserved {
        acme.call()
        return shipmentReserved { order = command.order }
    }
}

/**
 * Reserves a shipment at once, on the thread that posts the command.
 */
internal class InstantReservations : AbstractAssignee() {

    @Assign
    internal fun reserve(command: ReserveShipment): ShipmentReserved =
        shipmentReserved { order = command.order }
}

/**
 * Chooses a carrier for an order, which takes a call to a remote service, and asks
 * to reserve a shipment.
 *
 * @param carriers The stub of the service that tells which carrier to use.
 */
internal class ShipmentPlanner(private val carriers: RemoteService) : AsyncCommander() {

    @Command
    internal fun on(command: PlanShipment): ReserveShipment {
        carriers.call()
        return reserveShipment { order = command.order }
    }

    @Command
    internal fun on(event: OrderPaid): ReserveShipment {
        carriers.call()
        return reserveShipment { order = event.order }
    }
}

/**
 * Verifies a reserved shipment with the carrier.
 *
 * @param acme The stub of the API of the carrier.
 */
internal class ShipmentVerifier(private val acme: RemoteService) : AsyncEventReactor() {

    @React
    internal fun on(event: ShipmentReserved): ShipmentVerified {
        acme.call()
        return shipmentVerified { order = event.order }
    }
}

/**
 * Verifies a shipment reserved in another Bounded Context.
 *
 * @param acme The stub of the API of the carrier.
 */
internal class ExternalShipmentVerifier(private val acme: RemoteService) : AsyncEventReactor() {

    @React
    internal fun on(@External event: ShipmentReserved): ShipmentVerified {
        acme.call()
        return shipmentVerified { order = event.order }
    }
}

/**
 * Uploads the papers of a reserved shipment to an external archive.
 *
 * @param archive The stub of the archive.
 */
internal class ShipmentArchive(private val archive: RemoteService) : AsyncEventSubscriber() {

    private val uploaded = CopyOnWriteArrayList<OrderId>()

    /**
     * Obtains the orders whose papers were uploaded.
     */
    fun uploaded(): List<OrderId> = uploaded.toList()

    @Subscribe
    internal fun on(event: ShipmentReserved) {
        archive.call()
        uploaded.add(event.order)
    }
}

/**
 * Gets an order shipped: asks a carrier to reserve a shipment, and waits for
 * the reservation.
 *
 * The process issues [ReserveShipment], and gets [ShipmentReserved] as a later input.
 */
internal class OrderShippingProcess :
    ProcessManager<OrderId, OrderShipping, OrderShipping.Builder>() {

    @Command
    internal fun on(command: ShipOrder): ReserveShipment {
        alter { reservationRequested = true }
        return reserveShipment { order = command.order }
    }

    @React
    internal fun on(@Suppress("UNUSED_PARAMETER") event: ShipmentReserved): NoReaction {
        alter { shipmentReserved = true }
        return noReaction()
    }

    companion object {

        @Route
        @JvmStatic
        fun route(event: ShipmentReserved): OrderId = event.order
    }
}
