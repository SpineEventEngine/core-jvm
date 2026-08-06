/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server

import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.given.service.StatusCheckService
import io.spine.test.client.ClientTestContext.tasks
import io.spine.test.client.ClientTestContext.users
import io.spine.test.server.ServerStatus.OK
import io.spine.test.server.StatusCheckGrpc
import io.spine.testing.TestValues.randomString
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Server` should")
internal class ServerSpec {

    @Test
    @MuteLogging
    fun `allow registering custom gRPC services`() {
        val server = Server.atPort(EPHEMERAL)
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
    fun `provide 'CommandService', 'QueryService', and 'SubscriptionService'`() {
        val server = Server.atPort(EPHEMERAL)
            .add(users())
            .add(tasks())
            .build()

        server.subscriptionService() shouldNotBe null
        server.queryService() shouldNotBe null
        server.commandService() shouldNotBe null
    }

    @Test
    @MuteLogging
    fun `expose the port assigned by the operating system`() {
        val server = Server.atPort(EPHEMERAL).build()
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
                servers.add(Server.atPort(EPHEMERAL).build().apply { start() })
            }
            servers[0].boundPort shouldNotBe servers[1].boundPort
        } finally {
            servers.forEach { it.shutdown() }
        }
    }

    @Test
    @MuteLogging
    fun `prohibit obtaining the bound port before the start`() {
        val server = Server.atPort(EPHEMERAL).build()

        shouldThrow<IllegalStateException> {
            server.boundPort
        }
    }

    @Test
    @MuteLogging
    fun `prohibit obtaining the bound port of an in-process server`() {
        val server = Server.inProcess(randomString()).build()

        shouldThrow<IllegalStateException> {
            server.boundPort
        }
    }

    private companion object {

        const val ADDRESS = "localhost"

        /**
         * Instructs the operating system to assign a free port when the server starts.
         */
        const val EPHEMERAL = 0
    }
}
