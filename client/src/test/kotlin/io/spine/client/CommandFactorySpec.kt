/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.client

import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.base.Identifier.newUuid
import io.spine.client.given.CommandFactoryTestEnv.INVALID_COMMAND
import io.spine.core.UserId
import io.spine.core.tenantId
import io.spine.test.commands.cmdCreateProject
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.time.Future
import io.spine.testing.time.Past
import io.spine.time.ZoneIds
import io.spine.validation.ValidationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Command factory should")
internal class CommandFactorySpec {

    companion object {

        private val ACTOR: UserId = GivenUserId.of(newUuid())
        private val ZONE_ID: io.spine.time.ZoneId = ZoneIds.systemDefault()

        fun requestFactoryBuilder(): ActorRequestFactory.Builder {
            return ActorRequestFactory.newBuilder()
        }

        fun requestFactory(): ActorRequestFactory {
            return requestFactoryBuilder().setZoneId(ZONE_ID)
                .setActor(ACTOR)
                .build()
        }
    }

    private lateinit var factory: CommandFactory

    @BeforeEach
    fun createFactory() {
        factory = requestFactory().command()
    }

    @Nested
    internal inner class `create command` {

        /**
         * Tests that a command is created with the current time.
         *
         * @implNote We are creating a range of +/- second between the call to make sure the
         * timestamp would fit into this range. This way the test ensures the sub-second
         * precision of timestamps, which is enough for the purpose of this test.
         */
        @Test
        fun `with current time`() {
            val beforeCall = Past.secondsAgo(1)
            val command = factory.create(command())
            val afterCall = Future.secondsFromNow(1)
            command.isBetween(beforeCall, afterCall) shouldBe true
        }

        @Test
        fun `with given entity version`() {
            val command = factory.create(command(), 2)
            val context = command.context()
            context.targetVersion shouldBe 2
        }

        @Test
        fun `with own tenant ID`() {
            val tenantId = tenantId { value = javaClass.simpleName }
            val mtFactory = requestFactoryBuilder().also { f ->
                f.setTenantId(tenantId)
                f.actor = ACTOR
            }.build()

            val command = mtFactory.command().create(command())

            command.context().actorContext.tenantId shouldBe tenantId
        }

        private fun command(): CommandMessage = cmdCreateProject { id = newUuid() }
    }

    @Nested
    @DisplayName("throw `ValidationException` when creating command")
    internal inner class NotAccept {

        @Test
        fun `from invalid 'Message'`() {
            assertThrows<ValidationException> {
                factory.create(INVALID_COMMAND)
            }
        }

        @Test
        fun `from invalid 'Message' with version`() {
            assertThrows<ValidationException> {
                factory.create(INVALID_COMMAND, 42)
            }
        }
    }
}
