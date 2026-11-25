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

import io.spine.server.BoundedContext
import io.spine.server.async.AsyncDispatch
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.type.EventEnvelope

/**
 * The abstract base for event reactors that handle events outside the thread
 * that dispatches them.
 *
 * Use this class when reacting to an event takes long, for example, because the reactor
 * calls an external service and waits for its response. With an [AbstractEventReactor],
 * whoever posts the event waits for the reactor.
 *
 * A reacting method of an asynchronous reactor is declared the same way as in
 * an `AbstractEventReactor`, and may wait for as long as it needs. In Kotlin:
 *
 * ```kotlin
 * class ApplicationVerifier : AsyncEventReactor() {
 *
 *     @React
 *     internal fun on(event: ApplicationSubmitted): ApplicationVerified =
 *         verification.verify(event)
 * }
 * ```
 *
 * In Java:
 *
 * ```java
 * final class ApplicationVerifier extends AsyncEventReactor {
 *
 *     @React
 *     ApplicationVerified on(ApplicationSubmitted event) {
 *         return verification.verify(event);
 *     }
 * }
 * ```
 *
 * When the method returns, its events are posted to the `EventBus`.
 *
 * ## How the events are handled
 *
 * The reactor passes each event to the [AsyncExecution][io.spine.server.async.AsyncExecution]
 * of its Bounded Context, which decides when, and on which thread, the method is called.
 * This has the following consequences:
 *
 *  - The method is not called on the thread that posts the event.
 *  - Several threads may call the methods of one reactor at once, so the reactor must be
 *    safe for that.
 *  - The events are not necessarily handled in the order they were posted.
 *  - An accepted event is kept in memory only. If the JVM stops before the method
 *    returns, the event is not handled again.
 *
 * @see io.spine.server.BoundedContextBuilder.setAsyncExecution
 */
public abstract class AsyncEventReactor : AbstractEventReactor() {

    private val async = AsyncDispatch()

    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        async.registerWith(context)
    }

    /**
     * Passes the event to the asynchronous execution of the Bounded Context, which later
     * dispatches the event to its receptor and posts the resulting events.
     *
     * @return The outcome telling that the event is passed to the execution.
     */
    final override fun dispatch(event: EventEnvelope): DispatchOutcome =
        async.submit(this, event) { super.dispatch(event) }
}
