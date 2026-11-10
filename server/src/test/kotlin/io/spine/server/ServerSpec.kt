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

import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.server.given.service.StatusCheckService
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.server.ServerStatus.OK
import io.spine.test.server.StatusCheckGrpc
import io.spine.testing.TestValues.randomString
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks [Server].
 */
@DisplayName("`Server` should")
internal class ServerSpec {

    @Test
    @MuteLogging
    fun `allow registering custom gRPC services`() {
        val server = Server.atPort(ANY_FREE_PORT)
            .setApplication(applicationOf(users(), tasks()))
            .include(StatusCheckService())
            .build()
        server.start()
        val channel = ManagedChannelBuilder.forAddress(ADDRESS, server.boundPort)
            .usePlaintext()
            .build()
        try {
            val client = StatusCheckGrpc.newBlockingStub(channel)
            val response = client.check(Empty.getDefaultInstance())
            response.status shouldBe OK
        } finally {
            channel.shutdownNow()
            server.shutdown()
        }
    }

    @Test
    @MuteLogging
    fun `provide 'CommandService', 'QueryService', and 'SubscriptionService'`() {
        val server = server(applicationOf(users(), tasks()))
        // The server is started only to be shut down, which closes its application.
        server.start()
        try {
            server.subscriptionService() shouldNotBe null
            server.queryService() shouldNotBe null
            server.commandService() shouldNotBe null
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `refuse to be built without an application`() {
        shouldThrow<IllegalStateException> {
            Server.atPort(ANY_FREE_PORT).build()
        }
    }

    @Test
    fun `refuse to be built for a closed application`() {
        val application = applicationOf(users())
        application.close()

        shouldThrow<IllegalStateException> {
            server(application)
        }
    }

    @Test
    @MuteLogging
    fun `expose the port assigned by the operating system`() {
        val server = server(applicationOf(users()))
        server.start()
        try {
            server.boundPort shouldBeGreaterThan 0
        } finally {
            server.shutdown()
        }
    }

    /**
     * This is the regression guard for the intermittent `BindException` on CI:
     * asking for the port `0` twice must never yield the same port.
     *
     * See [issue #1652](https://github.com/SpineEventEngine/core-jvm/issues/1652).
     */
    @Test
    @MuteLogging
    fun `assign a distinct port to each server started at the port 0`() {
        val servers = mutableListOf<Server>()
        try {
            repeat(2) {
                servers.add(server(applicationOf(users())).apply { start() })
            }
            servers[0].boundPort shouldNotBe servers[1].boundPort
        } finally {
            servers.forEach { it.shutdown() }
        }
    }

    @Test
    @MuteLogging
    fun `prohibit obtaining the bound port before the start`() {
        applicationOf(users()).use {
            val server = server(it)

            shouldThrow<IllegalStateException> {
                server.boundPort
            }
        }
    }

    @Test
    @MuteLogging
    fun `close its application when shut down and awaited`() {
        val application = applicationOf(users())
        val server = server(application)
        server.start()

        server.shutdownAndWait()

        application.isOpen.shouldBeFalse()
        application.contexts().single().isOpen.shouldBeFalse()
    }

    @Test
    @MuteLogging
    fun `close its application when shut down and awaited, even if it was not started`() {
        val application = applicationOf(users())
        val server = server(application)

        shouldThrow<IllegalStateException> {
            server.shutdownAndWait()
        }

        application.isOpen.shouldBeFalse()
    }

    @Test
    @MuteLogging
    fun `close every context when shut down, even if closing one of them fails`() {
        val application = applicationOf(
            BoundedContext.singleTenant("Calculators").add(FailingCalculatorRepository()),
            users()
        )
        val server = server(application)
        server.start()

        shouldNotThrowAny {
            server.shutdown()
        }

        application.contexts().forEach { it.isOpen.shouldBeFalse() }
    }

    @Test
    @MuteLogging
    fun `prohibit obtaining the bound port of an in-process server`() {
        applicationOf(users()).use {
            val server = Server.inProcess(randomString())
                .setApplication(it)
                .build()

            shouldThrow<IllegalStateException> {
                server.boundPort
            }
        }
    }

    private fun applicationOf(vararg contexts: BoundedContextBuilder): Application =
        application {
            serverEnvironment = serverEnvironment {}
            contexts.forEach { add(it) }
        }

    private fun server(application: Application): Server =
        Server.atPort(ANY_FREE_PORT)
            .setApplication(application)
            .build()

    /**
     * A repository of calculators that throws from `close()` after closing its resources.
     */
    private class FailingCalculatorRepository : CalculatorRepository() {

        override fun close() {
            super.close()
            error(CLOSE_FAILURE)
        }
    }

    private companion object {

        const val ADDRESS = "localhost"

        const val CLOSE_FAILURE = "The repository failed to close."

        /**
         * Tells the operating system to pick a free port when the server binds.
         *
         * This is not a port number of its own — the port actually taken is known only
         * from [Server.getBoundPort], and only once the server has started.
         */
        const val ANY_FREE_PORT = 0
    }
}
