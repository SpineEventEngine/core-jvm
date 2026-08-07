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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.testing.TestValues.randomString
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

        private val container = GrpcContainer.atPort(PORT).build()

        @Test
        fun `obtain the port`() {
            container.hasPort() shouldBe true
            container.port shouldBe PORT
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
            val builder = GrpcContainer.atPort(PORT)

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

    private companion object {

        const val PORT = 0
    }
}
