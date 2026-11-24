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

/**
 * The abstract base for command assignees that handle commands outside the thread
 * that dispatches them.
 *
 * Use this class when handling a command takes long, for example, because the handler
 * calls an external service and waits for its response. With an [AbstractAssignee],
 * whoever posts the command waits for the handler. If a process manager posts the command,
 * the delivery of messages to it, and to the other entities served by the same shard,
 * waits too.
 *
 * A command-handling method of an asynchronous assignee is declared the same way as in
 * an `AbstractAssignee`, and may wait for as long as it needs:
 *
 * ```java
 * final class AcmeRpcHandler extends AsyncAssignee {
 *
 *     @Assign
 *     ShipmentReserved reserve(ReserveShipment command) {
 *         return acme.reserve(command);
 *     }
 * }
 * ```
 *
 * When the method returns, its events are posted to the `EventBus`. Rejections and failures
 * of the method are treated the same way as for an `AbstractAssignee`.
 *
 * ## How the commands are handled
 *
 * The assignee passes each command to the [AsyncExecution][io.spine.server.async.AsyncExecution]
 * of its Bounded Context, which decides when, and on which thread, the method is called.
 * This has the following consequences:
 *
 *  - The method is not called on the thread that posts the command.
 *  - Several threads may call the methods of one assignee at once, so the assignee must be
 *    safe for that.
 *  - The commands are not necessarily handled in the order they were posted.
 *  - An accepted command is kept in memory only. If the JVM stops before the method
 *    returns, the command is not handled again.
 *
 * @see io.spine.server.BoundedContextBuilder.setAsyncExecution
 */
public abstract class AsyncAssignee : AbstractAssignee() {

    private val async = AsyncDispatch()

    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        async.registerWith(context)
    }

    /**
     * Passes the command to the asynchronous execution of the Bounded Context, which later
     * dispatches the command to its receptor and posts the resulting events.
     *
     * @return The outcome telling that the command is passed to the execution.
     */
    final override fun dispatch(envelope: CommandEnvelope): DispatchOutcome =
        async.submit(this, envelope) { super.dispatch(envelope) }
}
