/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
package io.spine.core

import com.google.protobuf.Duration
import com.google.protobuf.Timestamp
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.base.Identifier.newUuid
import io.spine.protobuf.Durations2.seconds
import io.spine.test.commands.CmdCreateProject
import io.spine.test.commands.CmdStartProject
import io.spine.test.commands.CmdStopProject
import io.spine.test.commands.cmdCreateProject
import io.spine.test.commands.cmdStartProject
import io.spine.test.commands.cmdStopProject
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.client.command.testCommandMessage
import io.spine.testing.core.given.GivenCommandContext
import io.spine.testing.time.Future
import io.spine.testing.time.Past.minutesAgo
import io.spine.testing.time.Past.secondsAgo
import java.util.stream.Stream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for the [Command] class, which implements [CommandMixin].
 *
 * The test suite is located under the "client" module since actor request generation
 * is required. So we want to avoid circular dependencies between "core" and "client" modules.
 */
@DisplayName("`Command` should")
internal class CommandSpec {
    private val requestFactory = TestActorRequestFactory(CommandSpec::class.java)

    private lateinit var createProject: CmdCreateProject
    private lateinit var startProject: CmdStartProject
    private lateinit var stopProject: CmdStopProject

    @BeforeEach
    fun createCommands() {
        val projectId = newUuid()
        createProject = cmdCreateProject { id = projectId }
        startProject = cmdStartProject { id = projectId }
        stopProject = cmdStopProject { id = projectId }
    }

    @Test
    fun `extract message from given command`() {
        val message = testCommandMessage { id = newUuid() }
        val command = command(message)
        command.enclosedMessage() shouldBe message
    }

    @Nested
    internal inner class `Compare creation time if` {

        @Test
        fun `after given time`() {
            val command = command(stopProject)
            command.isAfter(secondsAgo(5)) shouldBe true
        }

        @Test
        fun `before given time`() {
            val command = command(startProject)
            command.isBefore(Future.secondsFromNow(10)) shouldBe true
        }

        @Test
        fun `withing time range`() {
            val fiveMinsAgo = command(createProject, minutesAgo(5))
            val twoMinsAgo = command(startProject, minutesAgo(2))
            val thirtySecondsAgo = command(stopProject, secondsAgo(30))
            val twentySecondsAgo = command(stopProject, secondsAgo(20))
            val fiveSecondsAgo = command(stopProject, secondsAgo(5))
            val commands = Stream.of(
                fiveMinsAgo,
                twoMinsAgo,
                thirtySecondsAgo,
                twentySecondsAgo,
                fiveSecondsAgo
            )
            val filteredCommands = commands.filter { c ->
                c.isBetween(minutesAgo(3), secondsAgo(10))
            }
            filteredCommands.count() shouldBe 3
        }
    }

    @Test
    fun `consider command scheduled when command delay is set`() {
        val cmd = commandWithDelay(createProject, seconds(10))
        cmd.isScheduled shouldBe true
    }

    @Test
    fun `consider command not scheduled when no scheduling options are present`() {
        val cmd = command(createProject)
        cmd.isScheduled shouldBe false
    }

    @Test
    fun `throw exception when command delay set to negative`() {
        val cmd = commandWithDelay(createProject, seconds(-10))
        assertThrows<IllegalStateException> { cmd.isScheduled }
    }

    private fun command(msg: CommandMessage, timestamp: Timestamp): Command {
        return requestFactory.createCommand(msg, timestamp)
    }

    private fun command(msg: CommandMessage): Command {
        return requestFactory.command().create(msg)
    }

    private fun commandWithDelay(msg: CommandMessage, delay: Duration): Command {
        val context = GivenCommandContext.withScheduledDelayOf(delay)
        return requestFactory.command()
            .createBasedOnContext(msg, context)
    }
}
