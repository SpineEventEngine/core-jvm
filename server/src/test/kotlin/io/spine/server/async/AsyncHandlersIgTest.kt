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

package io.spine.server.async

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Identifier.newUuid
import io.spine.environment.Tests
import io.spine.server.Application
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.application
import io.spine.server.async.given.AcmeRpcHandler
import io.spine.server.async.given.OrderShippingProcess
import io.spine.server.async.given.RemoteService
import io.spine.server.async.given.newOrder
import io.spine.server.async.given.RemoteService.Companion.waitSeconds
import io.spine.server.delivery.Delivery
import io.spine.server.delivery.DeliveryMonitor
import io.spine.server.delivery.DeliveryStats
import io.spine.server.delivery.LocalDispatchingObserver
import io.spine.server.delivery.UniformAcrossAllShards
import io.spine.server.given.context.shipping.OrderId
import io.spine.server.given.context.shipping.OrderShipping
import io.spine.server.given.context.shipping.command.shipOrder
import io.spine.server.nodeId
import io.spine.server.serverEnvironment
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.testing.server.blackbox.BlackBox
import io.spine.testing.server.blackbox.assertEntity
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks the scenario asynchronous handlers are made for.
 *
 * A process manager gets `ShipOrder` through its inbox and issues `ReserveShipment`.
 * An asynchronous assignee handles that command by calling the carrier, which takes its time.
 * The `Delivery` must finish with the inbox of the process manager without waiting for
 * the carrier. When the carrier responds, the process manager gets `ShipmentReserved`
 * as its next input.
 *
 * The `Delivery` of the tests delivers a message on the thread that posts it, as the default
 * one of the `Tests` environment does. So if a handler kept the `Delivery` waiting,
 * the posting of `ShipOrder` would not return, and the test would fail on a timeout.
 */
@DisplayName("An asynchronous assignee called by a process manager should")
internal class AsyncHandlersIgTest {

    /**
     * The stub of the carrier, which holds each call until the test releases it.
     */
    private val acme = RemoteService.held()

    private val deliveries = CompletedDeliveries()

    private lateinit var storageFactory: StorageFactory
    private lateinit var application: Application
    private lateinit var shipping: BlackBox

    @BeforeEach
    fun openShipping() {
        storageFactory = InMemoryStorageFactory.newInstance()
        val delivery = Delivery.newBuilder(nodeId { value = newUuid() }, storageFactory)
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setMonitor(deliveries)
            .build()
        delivery.subscribe(LocalDispatchingObserver(delivery))
        val execution = AsyncExecution.newBuilder()
            .setLimit(AcmeRpcHandler::class.java, 2)
            .build()
        val context = assumingTests("Shipping")
            .setAsyncExecution(execution)
            .add(OrderShippingProcess::class.java)
            .addAssignee(AcmeRpcHandler(acme))
        application = application {
            serverEnvironment = serverEnvironment {
                under<Tests> {
                    use(delivery)
                }
            }
            add(context)
        }
        shipping = BlackBox.from(application)
    }

    @AfterEach
    fun closeShipping() {
        acme.release()
        shipping.close()
        application.close()
        storageFactory.close()
    }

    @Test
    fun `let the Delivery finish with the inbox of the process while the carrier works`() {
        val order = newOrder()

        shipping.receivesCommand(shipOrder { this.order = order })

        // The posting has returned, so the delivery to the process is over.
        // The carrier still holds the call.
        acme.awaitCalls()
        deliveries.delivered() shouldBe 1
        val process = shippingOf(order)
        process.reservationRequested shouldBe true
        process.shipmentReserved shouldBe false
    }

    @Test
    fun `give the process the result as a later input when the carrier responds`() {
        val order = newOrder()
        shipping.receivesCommand(shipOrder { this.order = order })
        acme.awaitCalls()

        acme.release()

        // The second message delivered to the process is `ShipmentReserved`.
        deliveries.awaitDelivered(2)
        shippingOf(order).shipmentReserved shouldBe true
    }

    @Test
    fun `keep the calls to the carrier within the limit when many orders arrive`() {
        val orders = (1..5).map { newOrder() }

        orders.forEach {
            shipping.receivesCommand(shipOrder { order = it })
        }

        // All the five postings have returned, and each process has asked for a reservation.
        deliveries.delivered() shouldBe 5
        orders.forEach { shippingOf(it).reservationRequested shouldBe true }
        // The limit of the carrier is two calls at once, so three commands wait
        // outside the `Delivery`.
        acme.awaitCalls(2)
        acme.hasNewCall() shouldBe false

        acme.release()

        // The waiting commands get their turn as the earlier calls return.
        acme.awaitCalls(3)
        acme.calls() shouldHaveSize 5
        acme.mostCallsAtOnce() shouldBe 2
    }

    private fun shippingOf(order: OrderId): OrderShipping =
        shipping.assertEntity<OrderShippingProcess, _>(order)
            .actual()
            .shouldNotBeNull()
            .state()
            .shouldBeInstanceOf<OrderShipping>()
}

/**
 * Counts the messages the `Delivery` has delivered in the runs that are over.
 *
 * A run is over when its messages are handled and saved as delivered, so the count tells
 * a test how far the `Delivery` got.
 */
private class CompletedDeliveries : DeliveryMonitor() {

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var delivered = 0

    override fun onDeliveryCompleted(stats: DeliveryStats): Unit = lock.withLock {
        delivered += stats.deliveredCount()
        changed.signalAll()
    }

    /**
     * Obtains the number of messages delivered in the completed runs.
     */
    fun delivered(): Int = lock.withLock { delivered }

    /**
     * Waits until the given number of messages are delivered in the completed runs.
     */
    fun awaitDelivered(count: Int): Unit = lock.withLock {
        var remaining = SECONDS.toNanos(waitSeconds)
        while (delivered < count && remaining > 0) {
            remaining = changed.awaitNanos(remaining)
        }
        withClue("The `Delivery` should have delivered $count message(s)") {
            delivered shouldBe count
        }
    }
}
