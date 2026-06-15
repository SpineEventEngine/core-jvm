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

import io.kotest.matchers.shouldBe
import io.spine.protobuf.AnyPacker
import io.spine.testing.core.given.GivenVersion
import io.spine.validation.FieldAwareMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import io.kotest.assertions.throwables.shouldThrow

@DisplayName("`MessageIdMixin` should")
internal class MessageIdMixinSpec {

    @Nested inner class
    `identify an event message ID` {

        @Test
        fun `returning true for an event ID`() {
            val msgId = eventMessageId()
            msgId.isEvent shouldBe true
        }

        @Test
        fun `returning false for a command ID`() {
            val msgId = commandMessageId()
            msgId.isEvent shouldBe false
        }

        @Test
        fun `unpacking the event ID`() {
            val eventId = Events.generateId()
            val msgId = eventMessageId(eventId)
            msgId.asEventId() shouldBe eventId
        }

        @Test
        fun `throwing when not an event ID`() {
            val msgId = commandMessageId()
            shouldThrow<IllegalStateException> { msgId.asEventId() }
        }
    }

    @Nested inner class
    `identify a command message ID` {

        @Test
        fun `returning true for a command ID`() {
            val msgId = commandMessageId()
            msgId.isCommand shouldBe true
        }

        @Test
        fun `returning false for an event ID`() {
            val msgId = eventMessageId()
            msgId.isCommand shouldBe false
        }

        @Test
        fun `unpacking the command ID`() {
            val commandId = CommandId.generate()
            val msgId = commandMessageId(commandId)
            msgId.asCommandId() shouldBe commandId
        }

        @Test
        fun `throwing when not a command ID`() {
            val msgId = eventMessageId()
            shouldThrow<IllegalStateException> { msgId.asCommandId() }
        }
    }

    @Test
    fun `unpack the underlying message ID`() {
        val eventId = Events.generateId()
        val msgId = eventMessageId(eventId)
        msgId.id() shouldBe eventId
    }

    @Test
    fun `create a copy with version applied`() {
        val msgId = eventMessageId()
        val version = GivenVersion.withNumber(5)
        val withVersion = msgId.withVersion(version)
        withVersion.id shouldBe msgId.id
        withVersion.typeUrl shouldBe msgId.typeUrl
    }

    @Test
    fun `readValue on MessageId dispatches to each field by index`() {
        val msgId = eventMessageId()
        val fa = msgId as FieldAwareMessage
        msgId.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    private fun eventMessageId(eventId: EventId = Events.generateId()): MessageId =
        messageId {
            id = AnyPacker.pack(eventId)
            typeUrl = MessageIdMixin.EVENT_ID_TYPE_URL
        }

    private fun commandMessageId(commandId: CommandId = CommandId.generate()): MessageId =
        messageId {
            id = AnyPacker.pack(commandId)
            typeUrl = MessageIdMixin.COMMAND_ID_TYPE_URL
        }
}
