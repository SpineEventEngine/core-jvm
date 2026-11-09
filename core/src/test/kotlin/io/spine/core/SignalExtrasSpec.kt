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

import com.google.protobuf.StringValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Field
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker
import io.spine.test.core.projectCreated
import io.spine.test.core.projectId
import io.spine.testing.core.given.GivenCommandContext
import io.spine.testing.core.given.GivenTenantId
import io.spine.testing.core.given.GivenUserId
import io.spine.type.TypeName
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Covers `Signal` default methods, `EventField`, `EventContextField`,
 * `EnrichableMessageContext`, `CommandContextMixin`, and `ResponseMixin`.
 */
@DisplayName("Signal and context supplementary tests should")
internal class SignalExtrasSpec {

    @Nested inner class
    `Signal interface` {

        @Test
        fun `return enclosed type URL`() {
            val event = stubEvent()
            event.enclosedTypeUrl() shouldNotBe null
        }

        @Test
        fun `build an origin from this signal`() {
            val event = stubEvent()
            val origin = event.asMessageOrigin()
            origin shouldNotBe null
            origin.message shouldBe event.messageId()
        }

        @Test
        fun `return empty parent when no past message is set`() {
            val event = stubEvent()
            event.parent().shouldBeEmpty()
        }

        @Test
        fun `return parent when a past message origin is set`() {
            val event = eventWithPastMessage()
            event.parent().shouldBePresent()
        }

        @Test
        fun `return the identity builder with ID and type URL`() {
            val event = stubEvent()
            val builder = event.identityBuilder()
            builder shouldNotBe null
            builder.typeUrl.isNotBlank() shouldBe true
        }

        @Test
        fun `return the type of the enclosed message`() {
            val event = stubEvent()
            event.type() shouldNotBe null
        }
    }

    @Nested inner class
    `WithActor default methods` {

        @Test
        fun `return actor from context`() {
            val userId = GivenUserId.newUuid()
            val ctx = GivenCommandContext.withActorAndTime(userId, currentTime())
            ctx.actor() shouldBe userId
        }

        @Test
        fun `return tenant from context`() {
            val tenant = GivenTenantId.generate()
            val actorCtx = actorContext {
                actor = GivenUserId.newUuid()
                timestamp = currentTime()
                tenantId = tenant
            }
            val ctx = commandContext { actorContext = actorCtx }
            ctx.tenant() shouldBe tenant
        }
    }

    @Nested inner class
    `EnrichableMessageContext` {

        @Test
        fun `find an enrichment by type when present`() {
            val ctx = enrichedContext()
            val result = ctx.find(StringValue::class.java)
            result.shouldBePresent()
        }

        @Test
        fun `return empty when enrichment is absent`() {
            val ctx = bareContext()
            val result = ctx.find(StringValue::class.java)
            result.shouldBeEmpty()
        }

        @Test
        fun `get an enrichment by type when present`() {
            val sv = StringValue.of("enriched")
            val ctx = enrichedContext(sv)
            ctx.get(StringValue::class.java) shouldBe sv
        }

        @Test
        fun `throw when getting an absent enrichment`() {
            val ctx = bareContext()
            shouldThrow<IllegalStateException> { ctx.get(StringValue::class.java) }
        }
    }

    @Nested inner class
    `CommandContextMixin` {

        @Test
        fun `return actor context`() {
            val userId = GivenUserId.newUuid()
            val now = currentTime()
            val ctx = GivenCommandContext.withActorAndTime(userId, now)
            ctx.actorContext().actor shouldBe userId
        }
    }

    @Nested inner class
    `ResponseMixin` {

        @Test
        fun `return the error case of an error response`() {
            val response = response {
                status = Responses.errorWith(ErrorCase.REQUEST_DENIED)
            }
            response.errorCase() shouldBe ErrorCase.REQUEST_DENIED
        }
    }

    @Nested inner class
    `EventField` {

        @Test
        fun `construct with a field reference`() {
            val field = Field.named("message")
            val eventField = EventField(field)
            eventField shouldNotBe null
        }
    }

    @Nested inner class
    `EventContextField` {

        @Test
        fun `construct with a field reference`() {
            val field = Field.named("timestamp")
            val contextField = EventContextField(field)
            contextField shouldNotBe null
        }
    }

    // --- Helpers ---

    private fun actorContext(userId: UserId = GivenUserId.newUuid()): ActorContext =
        actorContext {
            actor = userId
            timestamp = currentTime()
            tenantId = GivenTenantId.generate()
        }

    private fun stubEvent(): @NonValidated Event {
        val project = projectId { id = "signal-extras-test" }
        val msg = projectCreated { projectId = project }
        val ctx = EventContext.newBuilder()
            .setImportContext(actorContext())
            .setTimestamp(currentTime())
            .buildPartial()
        return Event.newBuilder()
            .setId(Events.generateId())
            .setMessage(AnyPacker.pack(msg))
            .setContext(ctx)
            .buildPartial()
    }

    private fun eventWithPastMessage(): @NonValidated Event {
        val originId = Events.generateId()
        val rootMsgId = messageId {
            id = AnyPacker.pack(originId)
            typeUrl = MessageIdMixin.EVENT_ID_TYPE_URL
        }
        val originMsg = origin {
            message = rootMsgId
            actorContext = actorContext()
        }
        val ctx = EventContext.newBuilder()
            .setPastMessage(originMsg)
            .setTimestamp(currentTime())
            .buildPartial()
        val project = projectId { id = "with-origin-test" }
        val msg = projectCreated { projectId = project }
        return Event.newBuilder()
            .setId(Events.generateId())
            .setMessage(AnyPacker.pack(msg))
            .setContext(ctx)
            .buildPartial()
    }

    private fun containerWith(sv: StringValue): Enrichment.Container {
        val typeName = TypeName.of(StringValue::class.java).value()
        return Enrichment.Container.newBuilder()
            .putItems(typeName, AnyPacker.pack(sv))
            .build()
    }

    private fun enrichedContext(sv: StringValue = StringValue.of("test")):
        @NonValidated EventContext {
        val enrich = enrichment { container = containerWith(sv) }
        return EventContext.newBuilder()
            .setImportContext(actorContext())
            .setTimestamp(currentTime())
            .setEnrichment(enrich)
            .buildPartial()
    }

    private fun bareContext(): @NonValidated EventContext =
        EventContext.newBuilder()
            .setImportContext(actorContext())
            .setTimestamp(currentTime())
            .buildPartial()
}
