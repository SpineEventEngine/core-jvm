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

package io.spine.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.toJavaLogging
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.async.AsyncExecution
import io.spine.server.async.AsyncInvocation
import io.spine.server.async.given.AcmeRpcHandler
import io.spine.server.async.given.RemoteService
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.system.server.SystemContext
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.Interceptor
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks how a Bounded Context uses the execution of asynchronous handlers set in its builder.
 */
@DisplayName("`BoundedContext` should")
internal class BoundedContextAsyncExecutionSpec {

    private val execution = RecordingExecution()
    private val service = RemoteService.responding()
    private val context: BoundedContext = assumingTests("Shipping")
        .setAsyncExecution(execution)
        .addAssignee(AcmeRpcHandler(service))
        .build()

    @Test
    fun `pass the signal of an asynchronous handler to the execution set in the builder`() {
        context.use {
            val command = TestActorRequestFactory(javaClass)
                .createCommand(reserveShipment { order = newOrder() })

            context.commandBus().post(command, noOpObserver())

            val invocation = execution.submitted.single()
            invocation.handlerClass() shouldBe AcmeRpcHandler::class.java
            invocation.signal() shouldBe command
            // The recording execution has not run the invocation.
            service.calls().shouldBeEmpty()
        }
    }

    @Test
    fun `close the execution while the buses are still open`() {
        execution.onClose = {
            execution.busesWereOpen = context.commandBus().isOpen && context.eventBus().isOpen
        }

        context.close()

        execution.closed shouldBe 1
        execution.busesWereOpen shouldBe true
    }

    @Test
    fun `close the execution once`() {
        context.close()
        context.close()

        execution.closed shouldBe 1
    }

    @Test
    fun `close its System context if the execution refuses to serve the context`() {
        val shared = AsyncExecution.newBuilder().build()
        val systemLog = Interceptor(SystemContext::class.java, DEBUG.toJavaLogging())
        assumingTests("Orders")
            .setAsyncExecution(shared)
            .build().use {
                systemLog.intercept()
                try {
                    shouldThrow<IllegalStateException> {
                        assumingTests("Billing")
                            .setAsyncExecution(shared)
                            .build()
                    }

                    // A System context logs its name when it is closed.
                    systemLog.assertLog()
                        .record()
                        .hasMessageThat()
                        .contains("Billing")
                } finally {
                    systemLog.release()
                    context.close()
                }
            }
    }
}

/**
 * Remembers the submitted invocations without running them, and counts how many
 * times it was closed.
 */
private class RecordingExecution : AsyncExecution {

    val submitted = mutableListOf<AsyncInvocation>()
    var closed = 0
    var busesWereOpen = false
    var onClose: () -> Unit = {}

    override fun submit(invocation: AsyncInvocation) {
        submitted.add(invocation)
    }

    override fun close() {
        closed++
        onClose()
    }
}
