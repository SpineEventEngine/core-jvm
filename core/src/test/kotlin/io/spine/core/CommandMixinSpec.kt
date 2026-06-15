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

import com.google.protobuf.Duration
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker
import io.spine.test.core.projectCreated
import io.spine.test.core.projectId
import io.spine.testing.core.given.GivenCommandContext
import io.spine.testing.core.given.GivenTenantId
import io.spine.testing.core.given.GivenUserId
import io.spine.validation.FieldAwareMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`CommandMixin` should")
internal class CommandMixinSpec {

    @Test
    fun `return timestamp from actor context`() {
        val now = currentTime()
        val actor = GivenUserId.newUuid()
        val context = GivenCommandContext.withActorAndTime(actor, now)
        val command = commandWith(context)
        command.timestamp() shouldBe now
    }

    @Suppress("DEPRECATION")
    @Test
    fun `return time via deprecated 'time()' method`() {
        val context = GivenCommandContext.withRandomActor()
        val command = commandWith(context)
        command.time() shouldBe command.timestamp()
    }

    @Test
    fun `return actor context`() {
        val userId = GivenUserId.newUuid()
        val now = currentTime()
        val context = GivenCommandContext.withActorAndTime(userId, now)
        val command = commandWith(context)
        command.actorContext().actor shouldBe userId
    }

    @Nested inner class
    `report scheduling` {

        @Test
        fun `returning false for an unscheduled command`() {
            val command = commandWith(GivenCommandContext.withRandomActor())
            command.isScheduled shouldBe false
        }

        @Test
        fun `returning true when a delay is set`() {
            val delay = Duration.newBuilder().setSeconds(60).build()
            val context = GivenCommandContext.withScheduledDelayOf(delay)
            val command = commandWith(context)
            command.isScheduled shouldBe true
        }
    }

    @Nested inner class
    `return origin` {

        @Test
        fun `as empty when no origin set`() {
            val command = commandWith(GivenCommandContext.withRandomActor())
            command.origin().shouldBeEmpty()
        }

        @Test
        fun `as present when origin is set`() {
            val command = commandWithOrigin()
            command.origin().shouldBePresent()
        }
    }

    @Nested inner class
    `return root message` {

        @Test
        fun `returning own message ID when there is no origin`() {
            val command = commandWith(GivenCommandContext.withRandomActor())
            command.rootMessage() shouldBe command.messageId()
        }

        @Test
        fun `returning origin root when origin is present`() {
            val command = commandWithOrigin()
            val root = command.rootMessage()
            root shouldBe command.context().origin.root()
        }
    }

    @Test
    fun `readValue on Command dispatches to each field by index`() {
        val command = commandWith(GivenCommandContext.withRandomActor())
        val fa = command as FieldAwareMessage
        command.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    @Test
    fun `readValue on CommandId dispatches to each field by index`() {
        val cmdId = CommandId.generate()
        val fa = cmdId as FieldAwareMessage
        cmdId.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    @Test
    fun `CommandId value returns non-blank UUID string`() {
        val cmdId = CommandId.generate()
        cmdId.value() shouldNotBe null
        cmdId.value().isNotBlank() shouldBe true
    }

    private fun commandWith(commandCtx: CommandContext): Command {
        val project = projectId { id = "cmd-mixin-test" }
        val msg = projectCreated { projectId = project }
        return command {
            id = CommandId.generate()
            message = AnyPacker.pack(msg)
            context = commandCtx
        }
    }

    private fun commandWithOrigin(): Command {
        val originCmdId = CommandId.generate()
        val actorCtx = actorContext {
            actor = GivenUserId.newUuid()
            timestamp = currentTime()
            tenantId = GivenTenantId.generate()
        }
        val rootMsgId = messageId {
            id = AnyPacker.pack(originCmdId)
            typeUrl = MessageIdMixin.COMMAND_ID_TYPE_URL
        }
        val originMsg = origin {
            message = rootMsgId
            actorContext = actorCtx
        }
        val commandCtx = GivenCommandContext.withRandomActor().toBuilder()
            .setOrigin(originMsg)
            .build()
        return commandWith(commandCtx)
    }
}
