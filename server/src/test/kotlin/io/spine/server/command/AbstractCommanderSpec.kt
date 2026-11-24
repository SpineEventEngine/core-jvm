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
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.async.given.SignalMonitor
import io.spine.server.async.given.all
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.command.PlanShipment
import io.spine.server.given.context.shipping.command.ReserveShipment
import io.spine.server.given.context.shipping.command.planShipment
import io.spine.server.given.context.shipping.event.OrderPaid
import io.spine.server.given.context.shipping.event.orderPaid
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks how a commander reports a commanding method that fails.
 *
 * The other cases of `AbstractCommander` are in `AbstractCommanderTest`.
 */
@DisplayName("`AbstractCommander` should")
internal class AbstractCommanderSpec {

    private val orderId = newOrder()
    private val monitor = SignalMonitor()
    private val requests = TestActorRequestFactory(javaClass)
    private lateinit var context: BoundedContext

    @BeforeEach
    fun openContext() {
        val builder = assumingTests("Shipping")
            .addCommandDispatcher(UndecidedPlanner())
        monitor.watch(builder)
        context = builder.build()
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    @MuteLogging
    fun `report a failure of a commanding method that handles a command`() {
        val command = requests.createCommand(planShipment { order = orderId })

        context.commandBus().post(command, noOpObserver())

        val failed = monitor.all<HandlerFailedUnexpectedly>().single()
        failed.handledSignal shouldBe command.messageId()
        failed.error.message shouldBe UndecidedPlanner.reason
        monitor.all<ReserveShipment>().shouldBeEmpty()
    }

    @Test
    @MuteLogging
    fun `report a failure of a commanding method that handles an event`() {
        val event = TestEventFactory.newInstance(requests)
            .createEvent(orderPaid { order = orderId })

        context.eventBus().post(event)

        val failed = monitor.all<HandlerFailedUnexpectedly>().single()
        failed.handledSignal shouldBe event.messageId()
        failed.error.message shouldBe UndecidedPlanner.reason
        monitor.all<ReserveShipment>().shouldBeEmpty()
    }
}

/**
 * A commander whose commanding methods always fail.
 */
private class UndecidedPlanner : AbstractCommander() {

    @Command
    fun on(@Suppress("UNUSED_PARAMETER") command: PlanShipment): ReserveShipment =
        error(reason)

    @Command
    fun on(@Suppress("UNUSED_PARAMETER") event: OrderPaid): ReserveShipment =
        error(reason)

    companion object {

        /**
         * The message of the failure.
         */
        const val reason = "No carrier can be chosen."
    }
}
