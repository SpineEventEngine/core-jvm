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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.core.Command
import io.spine.core.tenantId
import io.spine.server.async.given.AcmeRpcHandler
import io.spine.server.async.given.RemoteService
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.server.type.CommandEnvelope
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AsyncInvocation` should")
internal class AsyncInvocationSpec {

    private val tenant = tenantId { value = "Tenant of the invocation" }
    private val command: Command = TestActorRequestFactory(javaClass, tenant)
        .createCommand(reserveShipment { order = newOrder() })
    private val service = RemoteService.responding()

    private fun invocation(handling: () -> Unit = { service.call() }): AsyncInvocation =
        AsyncInvocation(
            handlerClass = AcmeRpcHandler::class.java,
            envelope = CommandEnvelope.of(command),
            handling = handling
        )

    @Test
    fun `tell the class of the handler and the signal to handle`() {
        val invocation = invocation()

        invocation.handlerClass() shouldBe AcmeRpcHandler::class.java
        invocation.signal() shouldBe command
    }

    @Test
    fun `handle the signal on the calling thread`() {
        invocation().run()

        service.calls().single().thread shouldBe Thread.currentThread()
    }

    @Test
    fun `handle the signal under the tenant of the signal`() {
        invocation().run()

        service.calls().single().tenant shouldBe tenant
    }

    @Test
    @MuteLogging
    fun `handle the signal once`() {
        val invocation = invocation()
        invocation.run()

        shouldThrow<IllegalStateException> {
            invocation.run()
        }
        service.calls() shouldHaveSize 1
    }

    @Test
    @MuteLogging
    fun `throw what the handling throws`() {
        val failure = IllegalStateException("The events could not be posted.")
        val invocation = invocation { throw failure }

        val thrown = shouldThrow<IllegalStateException> {
            invocation.run()
        }

        thrown shouldBeSameInstanceAs failure
    }

    @Test
    fun `print the class of the handler and the type of the signal`() {
        val printed = invocation().toString()

        printed shouldContain AcmeRpcHandler::class.java.name
        printed shouldContain "ReserveShipment"
    }
}
