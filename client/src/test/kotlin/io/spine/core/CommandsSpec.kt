/*
 * Copyright 2022, TeamDev. All rights reserved.
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

import com.google.common.testing.NullPointerTester
import com.google.protobuf.Any
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.Duration
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations.ZERO
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.string.Stringifiers.fromString
import io.spine.string.Stringifiers.toString
import io.spine.test.commands.cmdCreateProject
import io.spine.test.commands.cmdStartProject
import io.spine.test.commands.cmdStopProject
import io.spine.testing.UtilityClassTest
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.setDefault
import io.spine.time.testing.Past.minutesAgo
import io.spine.time.testing.Past.secondsAgo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [Commands utility class][Commands].
 *
 * The test suite is located under the "client" module since actor request generation
 * is required. So we want to avoid circular dependencies between "core" and "client" modules.
 */
@DisplayName("`Commands` utility should")
internal class CommandsSpec : UtilityClassTest<Commands>(Commands::class.java) {

    private val requestFactory = TestActorRequestFactory(CommandsSpec::class.java)

    override fun configure(tester: NullPointerTester) {
        super.configure(tester)
        tester.setDefault<FileDescriptor>(DEFAULT_FILE_DESCRIPTOR)
            .setDefault<Timestamp>(currentTime())
            .setDefault<Duration>(ZERO)
            .setDefault<Command>(requestFactory.createCommand(createProject, minutesAgo(1)))
            .setDefault<CommandContext>(requestFactory.createCommandContext())
            .setDefault<UserId>(GivenUserId.newUuid())
    }

    @Test
    fun `sort given commands by timestamp`() {
        val cmd1 = requestFactory.createCommand(createProject, minutesAgo(1))
        val cmd2 = requestFactory.createCommand(startProject, secondsAgo(30))
        val cmd3 = requestFactory.createCommand(stopProject, secondsAgo(5))
        val sortedCommands = listOf(cmd1, cmd2, cmd3)
        val commandsToSort = listOf(cmd3, cmd1, cmd2)

        commandsToSort shouldNotBe sortedCommands
        Commands.sort(commandsToSort)
        commandsToSort shouldBe sortedCommands
    }

    @Test
    fun `provide stringifier for command id`() {
        val id = CommandId.generate()
        val str = toString(id)
        val convertedBack = fromString(str, CommandId::class.java)

        convertedBack shouldBe id
    }

    companion object {
        private val DEFAULT_FILE_DESCRIPTOR = Any.getDescriptor().file
        private val createProject = cmdCreateProject { id = Identifier.newUuid() }
        private val startProject = cmdStartProject { id = Identifier.newUuid() }
        private val stopProject = cmdStopProject { id = Identifier.newUuid() }
    }
}
