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

import io.spine.server.BoundedContext
import io.spine.server.async.AsyncDispatch
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope

/**
 * The abstract base for commanders that generate commands outside the thread that
 * dispatches the incoming message.
 *
 * Use this class when deciding which commands to generate takes long, for example, because
 * the commander calls an external service and waits for its response. With
 * an [AbstractCommander], whoever posts the incoming command or event waits for the commander.
 *
 * A commanding method of an asynchronous commander is declared the same way as in
 * an `AbstractCommander`, and may wait for as long as it needs. In Kotlin:
 *
 * ```kotlin
 * class ShipmentPlanner : AsyncCommander() {
 *
 *     @Command
 *     internal fun on(event: OrderPaid): ReserveShipment {
 *         val carrier = carriers.chooseFor(event.order)
 *         return reserveWith(carrier, event)
 *     }
 * }
 * ```
 *
 * In Java:
 *
 * ```java
 * final class ShipmentPlanner extends AsyncCommander {
 *
 *     @Command
 *     ReserveShipment on(OrderPaid event) {
 *         var carrier = carriers.chooseFor(event.getOrder());
 *         return reserveWith(carrier, event);
 *     }
 * }
 * ```
 *
 * When the method returns, its commands are posted to the `CommandBus`.
 *
 * ## How the messages are handled
 *
 * The commander passes each incoming command or event to
 * the [AsyncExecution][io.spine.server.async.AsyncExecution] of its Bounded Context, which
 * decides when, and on which thread, the method is called. This has the following consequences:
 *
 *  - The method is not called on the thread that posts the message.
 *  - Several threads may call the methods of one commander at once, so the commander must be
 *    safe for that.
 *  - The messages are not necessarily handled in the order they were posted.
 *  - An accepted message is kept in memory only. If the JVM stops before the method
 *    returns, the message is not handled again.
 *
 * @see io.spine.server.BoundedContextBuilder.setAsyncExecution
 */
public abstract class AsyncCommander : AbstractCommander() {

    private val async = AsyncDispatch()

    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        async.registerWith(context)
    }

    /**
     * Passes the command to the asynchronous execution of the Bounded Context, which later
     * dispatches the command to its receptor and posts the generated commands.
     *
     * @return The outcome telling that the command is passed to the execution.
     */
    final override fun dispatch(command: CommandEnvelope): DispatchOutcome =
        async.submit(this, command) { super.dispatch(command) }

    /**
     * Passes the event to the asynchronous execution of the Bounded Context, which later
     * dispatches the event to its receptor and posts the generated commands.
     *
     * @return The outcome telling that the event is passed to the execution.
     */
    final override fun dispatchEvent(event: EventEnvelope): DispatchOutcome =
        async.submit(this, event) { super.dispatchEvent(event) }
}
