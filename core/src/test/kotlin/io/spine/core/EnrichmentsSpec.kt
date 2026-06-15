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

import com.google.protobuf.StringValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker
import io.spine.test.core.projectCreated
import io.spine.test.core.projectId
import io.spine.testing.core.given.GivenTenantId
import io.spine.testing.core.given.GivenUserId
import io.spine.type.TypeName
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`Enrichments` utility should")
internal class EnrichmentsSpec {

    @Nested inner class
    `find the enrichment container` {

        @Test
        fun `returning the container when enrichment mode is 'CONTAINER'`() {
            val context = enrichedContext()
            val result = Enrichments.containerIn(context)
            result.shouldBePresent()
        }

        @Test
        fun `returning empty when no container is present`() {
            val context = bareContext()
            val result = Enrichments.containerIn(context)
            result.shouldBeEmpty()
        }
    }

    @Nested inner class
    `search for an enrichment by type` {

        @Test
        fun `returning the enrichment when present`() {
            val sv = StringValue.of("hello")
            val container = containerWith(sv)
            val result = Enrichments.find(StringValue::class.java, container)
            result.shouldBePresent()
            result.get() shouldBe sv
        }

        @Test
        fun `returning empty when type is absent`() {
            val container = Enrichment.Container.getDefaultInstance()
            val result = Enrichments.find(StringValue::class.java, container)
            result.shouldBeEmpty()
        }
    }

    @Nested inner class
    `get an enrichment by type` {

        @Test
        fun `returning the enrichment when present`() {
            val sv = StringValue.of("world")
            val container = containerWith(sv)
            Enrichments.get(StringValue::class.java, container) shouldBe sv
        }

        @Test
        fun `throwing when the enrichment is absent`() {
            val container = Enrichment.Container.getDefaultInstance()
            shouldThrow<IllegalStateException> {
                Enrichments.get(StringValue::class.java, container)
            }
        }
    }

    @Nested inner class
    `clear enrichments` {

        @Test
        fun `removing enrichment from an event`() {
            val event = enrichedEvent()
            event.context.hasEnrichment() shouldBe true
            val cleared = Enrichments.clear(event)
            cleared.context.hasEnrichment() shouldBe false
        }

        @Test
        fun `removing enrichments from event and all ancestor contexts`() {
            val event = enrichedEvent()
            val cleared = Enrichments.clearAll(event)
            cleared.context.hasEnrichment() shouldBe false
        }

        @Test
        fun `removing enrichment from an event with EVENT_CONTEXT origin`() {
            val event = enrichedEventWithEventContextOrigin()
            event.context.hasEnrichment() shouldBe true
            val cleared = Enrichments.clear(event)
            cleared.context.hasEnrichment() shouldBe false
        }

        @Test
        fun `removing enrichments from all nested EVENT_CONTEXT ancestors`() {
            val event = enrichedEventWithEventContextOrigin()
            val cleared = Enrichments.clearAll(event)
            cleared.context.hasEnrichment() shouldBe false
        }
    }

    @Suppress("DEPRECATION")
    private fun enrichedEventWithEventContextOrigin(): @NonValidated Event {
        val innerCtx = eventContext {
            importContext = actorContext()
            timestamp = currentTime()
            enrichment = enrichment()
            producerId = Identifier.pack(GivenUserId.newUuid())
        }
        val outerCtx = eventContext {
            eventContext = innerCtx
            timestamp = currentTime()
            enrichment = enrichment()
            producerId = Identifier.pack(GivenUserId.newUuid())
        }
        val project = projectId { id = "event-context-origin-test" }
        val msg = projectCreated { projectId = project }
        return Event.newBuilder()
            .setId(Events.generateId())
            .setMessage(AnyPacker.pack(msg))
            .setContext(outerCtx)
            .buildPartial()
    }

    private fun containerWith(sv: StringValue): Enrichment.Container {
        val typeName = TypeName.of(StringValue::class.java).value()
        return Enrichment.Container.newBuilder()
            .putItems(typeName, AnyPacker.pack(sv))
            .build()
    }

    private fun enrichment(sv: StringValue = StringValue.of("test")): Enrichment =
        enrichment {
            container = containerWith(sv)
        }

    private fun actorContext(): ActorContext =
        actorContext {
            actor = GivenUserId.newUuid()
            timestamp = currentTime()
            tenantId = GivenTenantId.generate()
        }

    @Suppress("DEPRECATION")
    private fun enrichedContext(): EventContext =
        eventContext {
            commandContext = commandContext { actorContext = actorContext() }
            timestamp = currentTime()
            enrichment = enrichment()
            producerId = Identifier.pack(GivenUserId.newUuid())
        }

    @Suppress("DEPRECATION")
    private fun bareContext(): EventContext =
        EventContext.newBuilder()
            .setCommandContext(commandContext { actorContext = actorContext() })
            .setTimestamp(currentTime())
            .buildPartial()

    private fun enrichedEvent(): @NonValidated Event {
        val project = projectId { id = "enrichments-test" }
        val msg = projectCreated { projectId = project }
        return Event.newBuilder()
            .setId(Events.generateId())
            .setMessage(AnyPacker.pack(msg))
            .setContext(enrichedContext())
            .buildPartial()
    }
}
