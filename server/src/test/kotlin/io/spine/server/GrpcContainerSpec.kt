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

import io.grpc.Server as GrpcServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.testing.TestValues.randomString
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests the `port` and `serverName` accessors of [GrpcContainer] and of its builder.
 *
 * A container is exposed either at a port or in-process under a name — never both.
 * So each accessor has a "wrong mode" case, in which it must fail rather than
 * hand out a `null`.
 */
@DisplayName("`GrpcContainer` should")
internal class GrpcContainerSpec {

    @Nested inner class
    `when exposed at a port` {

        private val container = GrpcContainer.atPort(ANY_FREE_PORT).build()

        @Test
        fun `obtain the port`() {
            container.hasPort() shouldBe true
            container.port shouldBe ANY_FREE_PORT
        }

        /**
         * The requested port and the bound one are two different things — see [ANY_FREE_PORT].
         */
        @Test
        fun `obtain the bound port once started`() {
            container.start()
            try {
                container.port shouldBe ANY_FREE_PORT
                container.boundPort shouldBeGreaterThan 0
            } finally {
                container.shutdown()
            }
        }

        @Test
        fun `prohibit obtaining the server name`() {
            container.hasServerName() shouldBe false
            shouldThrow<NullPointerException> {
                container.serverName
            }
        }
    }

    @Nested inner class
    `when exposed in-process` {

        private val name = randomString()
        private val container = GrpcContainer.inProcess(name).build()

        @Test
        fun `obtain the server name`() {
            container.hasServerName() shouldBe true
            container.serverName shouldBe name
        }

        @Test
        fun `prohibit obtaining the port`() {
            container.hasPort() shouldBe false
            shouldThrow<NullPointerException> {
                container.port
            }
        }
    }

    /**
     * The accessors are declared by [ConnectionBuilder], which is also the base class of
     * `Server.Builder`, so the same contract must hold before a container is built.
     */
    @Nested inner class
    `when still a builder` {

        @Test
        fun `prohibit obtaining the server name of a port-based builder`() {
            val builder = GrpcContainer.atPort(ANY_FREE_PORT)

            builder.hasServerName() shouldBe false
            shouldThrow<NullPointerException> {
                builder.serverName
            }
        }

        @Test
        fun `prohibit obtaining the port of an in-process builder`() {
            val builder = GrpcContainer.inProcess(randomString())

            builder.hasPort() shouldBe false
            shouldThrow<NullPointerException> {
                builder.port
            }
        }
    }

    @Nested inner class
    `when the underlying server fails to start` {

        @Test
        fun `stay in the not-started state`() {
            val container = GrpcContainer.atPort(ANY_FREE_PORT).build()
            container.injectServer(UnbindableGrpcServer())

            shouldThrow<IOException> {
                container.start()
            }

            container.isShutdown shouldBe true
            shouldThrow<IllegalStateException> {
                container.boundPort
            }
        }

        /**
         * A failed start must leave the container startable again — if it recorded the
         * server anyway, this second attempt would fail the "started already" check
         * instead of reaching the binding.
         */
        @Test
        fun `allow starting again`() {
            val container = GrpcContainer.atPort(ANY_FREE_PORT).build()
            container.injectServer(UnbindableGrpcServer())

            repeat(2) {
                shouldThrow<IOException> {
                    container.start()
                }
            }
        }
    }

    /**
     * A gRPC server which cannot start, imitating a port that fails to bind.
     */
    private class UnbindableGrpcServer : GrpcServer() {

        override fun start(): GrpcServer = throw IOException("Cannot bind.")

        override fun shutdown(): GrpcServer = this

        override fun shutdownNow(): GrpcServer = this

        override fun isShutdown(): Boolean = true

        override fun isTerminated(): Boolean = true

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true

        override fun awaitTermination() = Unit
    }

    private companion object {

        /**
         * Tells the operating system to pick a free port when the server binds.
         *
         * This is not a port number of its own. A container built with it keeps reporting
         * `0` from [GrpcContainer.getPort] — that is the value it was *asked* for. The port
         * actually taken is known only from [GrpcContainer.getBoundPort], and only once the
         * container has started.
         */
        const val ANY_FREE_PORT = 0
    }
}
