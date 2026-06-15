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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker
import io.spine.testing.core.given.GivenTenantId
import io.spine.testing.core.given.GivenUserId
import io.spine.validation.FieldAwareMessage
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`EventContextMixin` should")
internal class EventContextMixinSpec {

    @Test
    fun `return timestamp`() {
        val now = currentTime()
        val ctx = importContextOrigin().toBuilder()
            .setTimestamp(now)
            .buildPartial()
        ctx.timestamp() shouldBe now
    }

    @Nested inner class
    `obtain actor context` {

        @Test
        fun `from a 'COMMAND_CONTEXT' origin`() {
            val userId = GivenUserId.newUuid()
            val ctx = commandContextOrigin(actor = userId)
            ctx.actorContext().actor shouldBe userId
        }

        @Suppress("DEPRECATION")
        @Test
        fun `from an 'EVENT_CONTEXT' origin by traversal`() {
            val userId = GivenUserId.newUuid()
            val innerCtx = commandContextOrigin(actor = userId)
            val outerCtx = EventContext.newBuilder()
                .setEventContext(innerCtx)
                .setTimestamp(currentTime())
                .buildPartial()
            outerCtx.actorContext().actor shouldBe userId
        }

        @Test
        fun `from a 'PAST_MESSAGE' origin`() {
            val userId = GivenUserId.newUuid()
            val ctx = pastMessageOrigin(actor = userId)
            ctx.actorContext().actor shouldBe userId
        }

        @Test
        fun `from an 'IMPORT_CONTEXT' origin`() {
            val userId = GivenUserId.newUuid()
            val ctx = importContextOrigin(actor = userId)
            ctx.actorContext().actor shouldBe userId
        }

        @Test
        fun `throwing when no origin is set`() {
            val ctx = EventContext.newBuilder()
                .setTimestamp(currentTime())
                .buildPartial()
            shouldThrow<IllegalStateException> { ctx.actorContext() }
        }
    }

    @Nested inner class
    `obtain root message` {

        @Test
        fun `returning the past message root for 'PAST_MESSAGE' origin`() {
            val ctx = pastMessageOrigin()
            ctx.rootMessage().shouldBePresent()
        }

        @Test
        fun `returning empty for 'IMPORT_CONTEXT' origin`() {
            val ctx = importContextOrigin()
            ctx.rootMessage().shouldBeEmpty()
        }

        @Suppress("DEPRECATION")
        @Test
        fun `returning a message ID when the deprecated root command ID is set`() {
            val cmdId = CommandId.generate()
            val ctx = EventContext.newBuilder()
                .setTimestamp(currentTime())
                .setRootCommandId(cmdId)
                .buildPartial()
            ctx.rootMessage().shouldBePresent()
        }
    }

    @Test
    fun `readValue dispatches to each field by index`() {
        val ctx = importContextOrigin()
        val fa = ctx as FieldAwareMessage
        ctx.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    @Test
    fun `return the producer of the event`() {
        val producerId = GivenUserId.newUuid()
        val ctx = importContextOrigin().toBuilder()
            .setProducerId(Identifier.pack(producerId))
            .buildPartial()
        ctx.producer() shouldBe producerId
    }

    private fun actorContext(userId: UserId = GivenUserId.newUuid()): ActorContext =
        actorContext {
            actor = userId
            timestamp = currentTime()
            tenantId = GivenTenantId.generate()
        }

    @Suppress("DEPRECATION")
    private fun commandContextOrigin(actor: UserId = GivenUserId.newUuid()):
        @NonValidated EventContext {
        val cmdCtx = commandContext { actorContext = actorContext(actor) }
        return EventContext.newBuilder()
            .setCommandContext(cmdCtx)
            .setTimestamp(currentTime())
            .buildPartial()
    }

    private fun pastMessageOrigin(actor: UserId = GivenUserId.newUuid()):
        @NonValidated EventContext {
        val originEventId = Events.generateId()
        val rootMsgId = messageId {
            id = AnyPacker.pack(originEventId)
            typeUrl = MessageIdMixin.EVENT_ID_TYPE_URL
        }
        val originMsg = origin {
            message = rootMsgId
            actorContext = actorContext(actor)
        }
        return EventContext.newBuilder()
            .setPastMessage(originMsg)
            .setTimestamp(currentTime())
            .buildPartial()
    }

    private fun importContextOrigin(actor: UserId = GivenUserId.newUuid()):
        @NonValidated EventContext =
        EventContext.newBuilder()
            .setImportContext(actorContext(actor))
            .setTimestamp(currentTime())
            .buildPartial()
}
