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
        command.descriptorForType.fields.forEach { field ->
            command.readValue(field) shouldBe command.getField(field)
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
