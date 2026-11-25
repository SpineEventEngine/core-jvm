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

import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.core.TenantId
import io.spine.core.tenantId
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.async.AsyncExecution
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.AfterEach

/**
 * The base for the suites of asynchronous handlers.
 *
 * A suite [opens][open] a Shipping context with the handler under test. The handler calls
 * the [service], which keeps the call until the test [releases][RemoteService.release] it.
 * So a test sees what happens while the handler method is running, and what happens after
 * it returns. The [monitor] tells which signals were posted.
 *
 * Unless a test asks otherwise, the context runs the handlers on the threads of
 * the execution that [AsyncExecution.newBuilder] builds, as a production context does.
 */
internal abstract class AbstractAsyncHandlerSpec {

    /**
     * The stub of the remote service the handler under test calls.
     */
    protected val service = RemoteService.held()

    protected val monitor = SignalMonitor()

    /**
     * The tenant of the signals posted to a multi-tenant context.
     */
    protected val tenant: TenantId = tenantId { value = "Tenant of async handlers" }

    private var openContext: BoundedContext? = null

    private var requests = TestActorRequestFactory(javaClass)

    /**
     * The context opened by the test.
     */
    protected val context: BoundedContext
        get() = checkNotNull(openContext) { "The test has not opened a context." }

    /**
     * Opens a Shipping context.
     *
     * @param multiTenant Tells if the context is multi-tenant. If so, the signals posted
     *   by the test belong to the [tenant].
     * @param onCallingThread If `true`, no execution is set for the context, so it runs
     *   the handlers the way it does by default in tests: on the calling thread.
     * @param handlers Adds the handlers to the builder of the context.
     */
    protected fun open(
        multiTenant: Boolean = false,
        onCallingThread: Boolean = false,
        handlers: BoundedContextBuilder.() -> Unit
    ): BoundedContext {
        val builder = assumingTests("Shipping", multiTenant)
        if (!onCallingThread) {
            builder.setAsyncExecution(AsyncExecution.newBuilder().build())
        }
        builder.handlers()
        monitor.watch(builder)
        requests = if (multiTenant) {
            TestActorRequestFactory(javaClass, tenant)
        } else {
            TestActorRequestFactory(javaClass)
        }
        return builder.build().also { openContext = it }
    }

    /**
     * Lets the held calls of the [service] return, and closes the context, which waits
     * for its asynchronous handlers.
     */
    @AfterEach
    fun closeContext() {
        service.release()
        openContext?.close()
    }

    /**
     * Posts the command to the `CommandBus` of the context.
     */
    protected fun post(command: CommandMessage) {
        context.commandBus().post(requests.createCommand(command), noOpObserver())
    }

    /**
     * Posts the event to the `EventBus` of the context.
     */
    protected fun post(event: EventMessage) {
        context.eventBus().post(TestEventFactory.newInstance(requests).createEvent(event))
    }

    /**
     * Wraps the command the way a bus does before it dispatches the command.
     */
    protected fun envelopeOf(command: CommandMessage): CommandEnvelope =
        CommandEnvelope.of(requests.createCommand(command))

    /**
     * Wraps the event the way a bus does before it dispatches the event.
     */
    protected fun envelopeOf(event: EventMessage): EventEnvelope =
        EventEnvelope.of(TestEventFactory.newInstance(requests).createEvent(event))
}
