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
