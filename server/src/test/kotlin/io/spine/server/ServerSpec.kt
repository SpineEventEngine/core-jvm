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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.client.given.ClientTasksTestEnv.createCTask
import io.spine.core.Ack
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.given.service.StatusCheckService
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.server.ServerStatus.OK
import io.spine.test.server.StatusCheckGrpc
import io.spine.testing.TestValues.randomString
import io.spine.testing.client.TestActorRequestFactory
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

        server.subscriptionService() shouldNotBe null
        server.queryService() shouldNotBe null
        server.commandService() shouldNotBe null
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
    fun `leave its application open when shut down`() {
        applicationOf(users()).use { application ->
            val server = server(application)
            server.start()

            server.shutdown()

            application.isOpen.shouldBeTrue()
            application.contexts().single().isOpen.shouldBeTrue()
        }
    }

    @Test
    @MuteLogging
    fun `leave its application open when shut down and awaited`() {
        applicationOf(users()).use { application ->
            val server = server(application)
            server.start()

            server.shutdownAndWait()

            application.isOpen.shouldBeTrue()
            application.contexts().single().isOpen.shouldBeTrue()
        }
    }

    @Test
    @MuteLogging
    fun `refuse to be shut down and awaited before it is started`() {
        applicationOf(users()).use { application ->
            val server = server(application)

            shouldThrow<IllegalStateException> {
                server.shutdownAndWait()
            }

            application.isOpen.shouldBeTrue()
        }
    }

    @Test
    @MuteLogging
    fun `share its application with other servers`() {
        applicationOf(tasks()).use { application ->
            val first = server(application).apply { start() }
            val second = server(application).apply { start() }
            try {
                first.shutdown()

                val command = TestActorRequestFactory(ServerSpec::class.java)
                    .createCommand(createCTask("Served after another server shut down"))
                val observer = memoizingObserver<Ack>()
                second.commandService().post(command, observer)
                observer.firstResponse().status.hasOk().shouldBeTrue()
            } finally {
                second.shutdown()
            }
        }
    }

    @Test
    @MuteLogging
    fun `serve the application set last, when the builder is reused`() {
        val users = applicationOf(users())
        val tasks = applicationOf(tasks())
        val builder = Server.atPort(ANY_FREE_PORT)
        builder.setApplication(users).build()

        val server = builder.setApplication(tasks).build()

        try {
            val command = TestActorRequestFactory(ServerSpec::class.java)
                .createCommand(createCTask("Built by a reused builder"))
            val observer = memoizingObserver<Ack>()
            server.commandService().post(command, observer)
            observer.firstResponse().status.hasOk().shouldBeTrue()
        } finally {
            users.close()
            tasks.close()
        }
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

    private companion object {

        const val ADDRESS = "localhost"

        /**
         * Tells the operating system to pick a free port when the server binds.
         *
         * This is not a port number of its own — the port actually taken is known only
         * from [Server.getBoundPort], and only once the server has started.
         */
        const val ANY_FREE_PORT = 0
    }
}
