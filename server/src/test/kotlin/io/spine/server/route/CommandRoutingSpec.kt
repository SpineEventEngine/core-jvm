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

package io.spine.server.route

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.CommandMessage
import io.spine.base.Identifier
import io.spine.core.CommandContext
import io.spine.server.route.CommandRouting.Companion.newInstance
import io.spine.server.type.CommandEnvelope
import io.spine.test.command.CmdCreateProject
import io.spine.test.command.ProjectId
import io.spine.test.route.RegisterUser
import io.spine.testing.TestValues
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`CommandRouting` should")
internal class CommandRoutingSpec {

    /** A custom default route.  */
    private val customDefault =
        CommandRoute { _: CommandMessage, _: CommandContext -> DEFAULT_ANSWER }

    /** A custom command path for `StringValue` command messages.  */
    private val customRoute =
        CommandRoute { _: RegisterUser, _: CommandContext -> CUSTOM_ANSWER }

    /** The object under tests.  */
    private lateinit var commandRouting: CommandRouting<Long>

    @BeforeEach
    fun setUp() {
        commandRouting = newInstance(Long::class.java)
    }

    @Test
    fun `have default route`() {
        commandRouting.defaultRoute() shouldNotBe null
        commandRouting.defaultRoute().shouldBeInstanceOf<DefaultCommandRoute<*>>()
    }

    @Test
    fun `replace default route`() {
        commandRouting.replaceDefault(customDefault) shouldBe commandRouting
        commandRouting.defaultRoute() shouldBe customDefault
    }

    @Test
    fun `add custom route`() {
        // Assert the result of the adding routing call. It modifies the routing.
        commandRouting.route<RegisterUser>(customRoute) shouldBe commandRouting

        commandRouting.find<RegisterUser>() shouldBe customRoute
    }

    @Test
    fun `not allow overwriting set route`() {
        commandRouting.route<RegisterUser>(customRoute)
        assertThrows<IllegalStateException> {
            commandRouting.route<RegisterUser>(customRoute)
        }
    }

    @Test
    fun `remove previously set route`() {
        commandRouting.route<RegisterUser>(customRoute)
        commandRouting.remove<RegisterUser>()
        commandRouting.find<RegisterUser>() shouldBe null
    }

    @Test
    fun `throw ISE on removal if route is not set`() {
        assertThrows<IllegalStateException> {
            commandRouting.remove<RegisterUser>()
        }
    }

    @Test
    fun `apply default route`() {
        // Replace the default route since we have a custom command message.
        commandRouting.replaceDefault(customDefault) // Have a custom route too.
            .route<RegisterUser>(customRoute)

        val cmd = CmdCreateProject.newBuilder()
            .setProjectId(ProjectId.newBuilder().setId(Identifier.newUuid()).build()).build()
        val command = CommandEnvelope.of(requestFactory.createCommand(cmd))

        val id = commandRouting(command.message(), command.context())

        id shouldBe DEFAULT_ANSWER
    }

    @Test
    fun `apply custom route`() {
        val cmd = RegisterUser.newBuilder().setId(TestValues.random(1, 100).toLong()).build()
        val command = CommandEnvelope.of(
            requestFactory.createCommand(cmd)
        )
        // Have a custom route.
        commandRouting.route(RegisterUser::class.java, customRoute)

        val id = commandRouting(command.message(), command.context())

        id shouldBe CUSTOM_ANSWER
    }

    companion object {
        private val requestFactory = TestActorRequestFactory(
            CommandRoutingSpec::class.java
        )

        /** Default result of the command routing function.  */
        private const val DEFAULT_ANSWER = 42L

        /** Custom result of the command routing function.  */
        private const val CUSTOM_ANSWER = 100500L
    }
}
