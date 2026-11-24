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

package io.spine.core

import com.google.protobuf.Timestamp
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.base.Identifier.newUuid
import io.spine.test.commands.CmdCreateProject
import io.spine.test.commands.CmdStartProject
import io.spine.test.commands.CmdStopProject
import io.spine.test.commands.cmdCreateProject
import io.spine.test.commands.cmdStartProject
import io.spine.test.commands.cmdStopProject
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.client.command.testCommandMessage
import io.spine.testing.time.Future
import io.spine.testing.time.Past.minutesAgo
import io.spine.testing.time.Past.secondsAgo
import java.util.stream.Stream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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

    private fun command(msg: CommandMessage, timestamp: Timestamp): Command {
        return requestFactory.createCommand(msg, timestamp)
    }

    private fun command(msg: CommandMessage): Command {
        return requestFactory.command().create(msg)
    }
}
