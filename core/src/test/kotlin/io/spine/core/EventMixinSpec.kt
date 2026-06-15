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

import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker
import io.spine.test.core.projectCreated
import io.spine.test.core.projectId
import io.spine.testing.core.given.GivenEnrichment
import io.spine.testing.core.given.GivenTenantId
import io.spine.testing.core.given.GivenUserId
import io.spine.validation.FieldAwareMessage
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`EventMixin` should")
internal class EventMixinSpec {

    @Test
    fun `return timestamp from event context`() {
        val now = currentTime()
        val event = eventWithCommandContext(timestamp = now)
        event.timestamp() shouldBe now
    }

    @Suppress("DEPRECATION")
    @Test
    fun `return time via deprecated 'time()' method`() {
        val now = currentTime()
        val event = eventWithCommandContext(timestamp = now)
        event.time() shouldBe now
    }

    @Nested inner class
    `report rejection status` {

        @Test
        fun `returning false for a regular event`() {
            val event = eventWithCommandContext()
            event.isRejection shouldBe false
        }

        @Test
        fun `returning true for an event with rejection context`() {
            val rejection = rejectionEvent()
            rejection.isRejection shouldBe true
        }
    }

    @Test
    fun `return actor context from command context origin`() {
        val userId = GivenUserId.newUuid()
        val event = eventWithCommandContext(actor = userId)
        event.actorContext().actor shouldBe userId
    }

    @Nested inner class
    `return origin` {

        @Test
        fun `as empty when there is no past message`() {
            val event = eventWithCommandContext()
            event.origin().shouldBeEmpty()
        }

        @Test
        fun `as present when there is a past message`() {
            val event = eventWithPastMessage()
            event.origin().shouldBePresent()
        }
    }

    @Test
    fun `return message ID containing event ID and enclosed type`() {
        val event = eventWithCommandContext()
        val msgId = event.messageId()
        msgId shouldNotBe null
        msgId.isEvent shouldBe true
    }

    @Test
    fun `return root message from context when present`() {
        val event = eventWithPastMessage()
        val root = event.rootMessage()
        root shouldNotBe null
    }

    @Test
    fun `return own message ID as root when no past message`() {
        val eventCtx = EventContext.newBuilder()
            .setImportContext(actorContext())
            .setTimestamp(currentTime())
            .buildPartial()
        val event = buildEvent(eventCtx)
        val root = event.rootMessage()
        root shouldBe event.messageId()
    }

    @Test
    fun `create a single-element set containing this event`() {
        val event = eventWithCommandContext()
        val set = event.toSet()
        set.size shouldBe 1
        set.first() shouldBe event
    }

    @Suppress("DEPRECATION")
    @Test
    fun `return the root command ID via deprecated method`() {
        val cmdId = CommandId.generate()
        val originMsgId = messageId {
            id = AnyPacker.pack(cmdId)
            typeUrl = MessageIdMixin.COMMAND_ID_TYPE_URL
        }
        val pastMessage = origin {
            message = originMsgId
            actorContext = actorContext()
        }
        val eventCtx = EventContext.newBuilder()
            .setPastMessage(pastMessage)
            .setTimestamp(currentTime())
            .buildPartial()
        val event = buildEvent(eventCtx)
        event.rootCommandId() shouldBe cmdId
    }

    @Test
    fun `return event ID value as string`() {
        val id = Events.generateId()
        id.value() shouldNotBe null
        id.value().isNotBlank() shouldBe true
    }

    @Test
    fun `readValue on Event dispatches to each field by index`() {
        val event = eventWithCommandContext()
        val fa = event as FieldAwareMessage
        event.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    @Test
    fun `readValue on EventId dispatches to each field by index`() {
        val eventId = Events.generateId()
        val fa = eventId as FieldAwareMessage
        eventId.descriptorForType.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    @Nested inner class
    `clear enrichments` {

        @Test
        fun `removing enrichment from event context`() {
            val event = enrichedEvent()
            event.context.hasEnrichment() shouldBe true
            val cleared = event.clearEnrichments()
            cleared.context.hasEnrichment() shouldBe false
        }

        @Test
        fun `removing enrichments from all ancestor contexts`() {
            val event = enrichedEvent()
            val cleared = event.clearAllEnrichments()
            cleared.context.hasEnrichment() shouldBe false
        }
    }

    private fun enrichedEvent(): @NonValidated Event {
        val enrich = GivenEnrichment.withOneAttribute()
        val eventCtx = eventContext {
            importContext = actorContext()
            timestamp = currentTime()
            enrichment = enrich
            producerId = io.spine.base.Identifier.pack(GivenUserId.newUuid())
        }
        val project = projectId { id = "event-mixin-test" }
        val msg = projectCreated { projectId = project }
        return Event.newBuilder()
            .setId(Events.generateId())
            .setMessage(AnyPacker.pack(msg))
            .setContext(eventCtx)
            .buildPartial()
    }

    private fun rejectionEvent(): @NonValidated Event {
        val rejectionCtx = EventContext.newBuilder()
            .setImportContext(actorContext())
            .setTimestamp(currentTime())
            .setRejection(RejectionEventContext.getDefaultInstance())
            .buildPartial()
        return buildEvent(rejectionCtx)
    }

    private fun eventWithPastMessage(): @NonValidated Event {
        val originEventId = Events.generateId()
        val originMsgId = messageId {
            id = AnyPacker.pack(originEventId)
            typeUrl = MessageIdMixin.EVENT_ID_TYPE_URL
        }
        val pastMessage = origin {
            message = originMsgId
            actorContext = actorContext()
        }
        val eventCtx = EventContext.newBuilder()
            .setPastMessage(pastMessage)
            .setTimestamp(currentTime())
            .buildPartial()
        return buildEvent(eventCtx)
    }

    private fun commandContext(userId: UserId = GivenUserId.newUuid()): CommandContext =
        commandContext {
            actorContext = actorContext {
                actor = userId
                timestamp = currentTime()
                tenantId = GivenTenantId.generate()
            }
        }

    private fun actorContext(userId: UserId = GivenUserId.newUuid()): ActorContext =
        actorContext {
            actor = userId
            timestamp = currentTime()
            tenantId = GivenTenantId.generate()
        }

    @Suppress("DEPRECATION")
    private fun eventWithCommandContext(
        timestamp: com.google.protobuf.Timestamp = currentTime(),
        actor: UserId = GivenUserId.newUuid()
    ): @NonValidated Event {
        val ctx = commandContext(actor)
        val eventCtx = EventContext.newBuilder()
            .setCommandContext(ctx)
            .setTimestamp(timestamp)
            .buildPartial()
        return buildEvent(eventCtx)
    }

    private fun buildEvent(eventCtx: EventContext): @NonValidated Event {
        val project = projectId { id = "event-mixin-test" }
        val msg = projectCreated { projectId = project }
        return Event.newBuilder()
            .setId(Events.generateId())
            .setMessage(AnyPacker.pack(msg))
            .setContext(eventCtx)
            .buildPartial()
    }
}
