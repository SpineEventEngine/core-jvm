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
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker
import io.spine.testing.core.given.GivenTenantId
import io.spine.testing.core.given.GivenUserId
import io.spine.validation.FieldAwareMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`OriginMixin` should")
internal class OriginMixinSpec {

    @Test
    fun `return the message ID of the origin`() {
        val eventId = Events.generateId()
        val msgId = eventMsgId(eventId)
        val origin = originFor(msgId)
        origin.messageId() shouldBe msgId
    }

    @Nested inner class
    `resolve the root message` {

        @Test
        fun `returning own message ID when there is no grand origin`() {
            val eventId = Events.generateId()
            val msgId = eventMsgId(eventId)
            val origin = originFor(msgId)
            origin.root() shouldBe msgId
        }

        @Test
        fun `traversing grand origins to find the root`() {
            val rootEventId = Events.generateId()
            val rootMsgId = eventMsgId(rootEventId)

            val rootOrigin = originFor(rootMsgId)

            val childEventId = Events.generateId()
            val childMsgId = eventMsgId(childEventId)
            val childOrigin = origin {
                message = childMsgId
                actorContext = actorContext()
                grandOrigin = rootOrigin
            }

            childOrigin.root() shouldBe rootMsgId
        }

        @Test
        fun `traversing a multi-level grand origin chain`() {
            val rootEventId = Events.generateId()
            val rootMsgId = eventMsgId(rootEventId)
            val root = originFor(rootMsgId)

            val mid = origin {
                message = eventMsgId(Events.generateId())
                actorContext = actorContext()
                grandOrigin = root
            }

            val leaf = origin {
                message = eventMsgId(Events.generateId())
                actorContext = actorContext()
                grandOrigin = mid
            }

            leaf.root() shouldBe rootMsgId
        }
    }

    @Test
    fun `readValue on Origin dispatches to each field by index`() {
        val eventId = Events.generateId()
        val origin = originFor(eventMsgId(eventId))
        val fa = origin as FieldAwareMessage
        origin.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    private fun actorContext(): ActorContext =
        actorContext {
            actor = GivenUserId.newUuid()
            timestamp = currentTime()
            tenantId = GivenTenantId.generate()
        }

    private fun eventMsgId(eventId: EventId): MessageId =
        messageId {
            id = AnyPacker.pack(eventId)
            typeUrl = MessageIdMixin.EVENT_ID_TYPE_URL
        }

    private fun originFor(msgId: MessageId): Origin =
        origin {
            message = msgId
            actorContext = actorContext()
        }
}
