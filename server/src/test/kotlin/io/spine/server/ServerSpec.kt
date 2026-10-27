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
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.given.service.StatusCheckService
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.client.users.UserAccount
import io.spine.test.server.ServerStatus.OK
import io.spine.test.server.StatusCheckGrpc
import io.spine.testing.TestValues.randomString
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Server` should")
internal class ServerSpec {

    @Test
    @MuteLogging
    fun `allow registering custom gRPC services`() {
        val server = Server.atPort(ANY_FREE_PORT)
            .add(users())
            .add(tasks())
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
        val server = Server.atPort(ANY_FREE_PORT)
            .add(users())
            .add(tasks())
            .build()
        // The server is started only to be shut down, which closes its contexts.
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
    @MuteLogging
    fun `expose the port assigned by the operating system`() {
        val server = Server.atPort(ANY_FREE_PORT).build()
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
                servers.add(Server.atPort(ANY_FREE_PORT).build().apply { start() })
            }
            servers[0].boundPort shouldNotBe servers[1].boundPort
        } finally {
            servers.forEach { it.shutdown() }
        }
    }

    @Test
    @MuteLogging
    fun `prohibit obtaining the bound port before the start`() {
        val server = Server.atPort(ANY_FREE_PORT).build()

        shouldThrow<IllegalStateException> {
            server.boundPort
        }
    }

    @Test
    @MuteLogging
    fun `close its contexts when shut down and awaited`() {
        val server = Server.atPort(ANY_FREE_PORT)
            .add(users())
            .build()
        server.start()
        contextServing(userAccount).shouldNotBeNull()

        server.shutdownAndWait()

        contextServing(userAccount).shouldBeNull()
    }

    @Test
    @MuteLogging
    fun `close its contexts when shut down and awaited, even if it was not started`() {
        val server = Server.atPort(ANY_FREE_PORT)
            .add(users())
            .build()
        contextServing(userAccount).shouldNotBeNull()

        shouldThrow<IllegalStateException> {
            server.shutdownAndWait()
        }

        contextServing(userAccount).shouldBeNull()
    }

    @Test
    @MuteLogging
    fun `prohibit obtaining the bound port of an in-process server`() {
        val server = Server.inProcess(randomString()).build()

        shouldThrow<IllegalStateException> {
            server.boundPort
        }
    }

    private fun contextServing(type: TypeUrl): BoundedContext? =
        ServerEnvironment.instance().findContextServing(type).orElse(null)

    private companion object {

        const val ADDRESS = "localhost"

        val userAccount: TypeUrl = TypeUrl.of(UserAccount::class.java)

        /**
         * Tells the operating system to pick a free port when the server binds.
         *
         * This is not a port number of its own — the port actually taken is known only
         * from [Server.getBoundPort], and only once the server has started.
         */
        const val ANY_FREE_PORT = 0
    }
}
