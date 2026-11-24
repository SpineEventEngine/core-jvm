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
 * The abstract base for event subscribers that handle events outside the thread
 * that dispatches them.
 *
 * Use this class when handling an event takes long, for example, because the subscriber
 * uploads a document to an external archive. With an [AbstractEventSubscriber],
 * whoever posts the event waits for the subscriber.
 *
 * A subscribing method of an asynchronous subscriber is declared the same way as in
 * an `AbstractEventSubscriber`, and may wait for as long as it needs:
 *
 * ```java
 * final class DocumentArchiver extends AsyncEventSubscriber {
 *
 *     @Subscribe
 *     void on(DocumentGenerated event) {
 *         archive.upload(event.getDocument());
 *     }
 * }
 * ```
 *
 * ## How the events are handled
 *
 * The subscriber passes each event to
 * the [AsyncExecution][io.spine.server.async.AsyncExecution] of its Bounded Context, which
 * decides when, and on which thread, the method is called. This has the following consequences:
 *
 *  - The method is not called on the thread that posts the event.
 *  - Several threads may call the methods of one subscriber at once, so the subscriber must be
 *    safe for that.
 *  - The events are not necessarily handled in the order they were posted.
 *  - An accepted event is kept in memory only. If the JVM stops before the method
 *    returns, the event is not handled again.
 *
 * @see io.spine.server.BoundedContextBuilder.setAsyncExecution
 */
public abstract class AsyncEventSubscriber : AbstractEventSubscriber() {

    private val async = AsyncDispatch()

    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        async.registerWith(context)
    }

    /**
     * Passes the event to the asynchronous execution of the Bounded Context, which later
     * passes the event to the subscribing method.
     *
     * @return The outcome telling that the event is passed to the execution.
     */
    final override fun handle(event: EventEnvelope): DispatchOutcome =
        async.submit(this, event) { super.handle(event) }
}
