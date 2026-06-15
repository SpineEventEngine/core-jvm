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

package io.spine.core

import com.google.protobuf.util.Timestamps
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Time.currentTime
import io.spine.core.Commands.ensureMessage
import io.spine.core.Commands.idStringifier
import io.spine.core.Commands.sort
import io.spine.protobuf.AnyPacker
import io.spine.test.core.createProject
import io.spine.test.core.projectId
import io.spine.testing.UtilityClassTest
import io.spine.testing.core.given.GivenCommandContext
import io.spine.testing.core.given.GivenUserId
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`Commands` utility should")
internal class CommandsSpec : UtilityClassTest<Commands>(Commands::class.java) {

    @Nested inner class
    `ensure command message` {

        @Test
        fun `returning enclosed message from a 'Command'`() {
            val command = stubCommand()
            val message = ensureMessage(command)
            message shouldNotBe null
        }

        @Test
        fun `returning enclosed message from an 'Any'`() {
            val project = projectId { id = "ensure-any-test" }
            val original = createProject { id = project }
            val packed = AnyPacker.pack(original)
            val message = ensureMessage(packed)
            message shouldBe original
        }
    }

    @Test
    fun `return command ID value as string`() {
        val id = CommandId.generate()
        id.value() shouldNotBe null
        id.value().isNotBlank() shouldBe true
    }

    @Test
    fun `return a stringifier for command IDs`() {
        idStringifier() shouldNotBe null
    }

    @Test
    fun `round-trip command ID through stringifier`() {
        val id = CommandId.generate()
        val str = idStringifier().convert(id)
        val restored = idStringifier().reverse().convert(str)
        restored shouldBe id
    }

    @Nested inner class
    `sort commands by timestamp` {

        @Test
        fun `ordering older commands first`() {
            val earlier = Timestamps.fromSeconds(1000)
            val later = Timestamps.fromSeconds(2000)
            val c1 = stubCommandWithTime(later)
            val c2 = stubCommandWithTime(earlier)
            val commands = mutableListOf(c1, c2)
            sort(commands)
            commands[0].timestamp() shouldBe earlier
            commands[1].timestamp() shouldBe later
        }

        @Test
        fun `leaving an already-sorted list unchanged`() {
            val t1 = Timestamps.fromSeconds(100)
            val t2 = Timestamps.fromSeconds(200)
            val c1 = stubCommandWithTime(t1)
            val c2 = stubCommandWithTime(t2)
            val commands = mutableListOf(c1, c2)
            sort(commands)
            commands[0].timestamp() shouldBe t1
            commands[1].timestamp() shouldBe t2
        }
    }

    private fun stubCommand(): @NonValidated Command {
        val project = projectId { id = javaClass.name }
        val msg = createProject { id = project }
        val context = GivenCommandContext.withRandomActor()
        return Command.newBuilder()
            .setId(CommandId.generate())
            .setMessage(AnyPacker.pack(msg))
            .setContext(context)
            .buildPartial()
    }

    private fun stubCommandWithTime(
        timestamp: com.google.protobuf.Timestamp
    ): @NonValidated Command {
        val project = projectId { id = "sort-test" }
        val msg = createProject { id = project }
        val actor = GivenUserId.newUuid()
        val context = GivenCommandContext.withActorAndTime(actor, timestamp)
        return Command.newBuilder()
            .setId(CommandId.generate())
            .setMessage(AnyPacker.pack(msg))
            .setContext(context)
            .buildPartial()
    }
}
