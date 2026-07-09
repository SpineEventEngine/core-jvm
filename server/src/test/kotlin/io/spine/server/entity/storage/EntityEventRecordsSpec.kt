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

package io.spine.server.entity.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.core.EventId
import io.spine.test.storage.event.StgProjectCreated
import io.spine.testdata.Sample
import io.spine.testing.server.TestEventFactory
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`EntityEventRecords` should")
internal class EntityEventRecordsSpec {

    private val entityId = "observed-entity"

    @Test
    fun `create a record from an event`() {
        val event = newEvent()

        val record = EntityEventRecords.create(entityId, event)

        record.id shouldBe event.id
        record.entityId shouldBe Identifier.pack(entityId)
        record.timestamp shouldBe event.context().timestamp
        record.event shouldBe event
    }

    @Test
    fun `reject an event without a context`() {
        val invalid = withoutContext()

        shouldThrow<IllegalArgumentException> {
            EntityEventRecords.create(entityId, invalid)
        }
    }

    @Test
    fun `reject an event without a message`() {
        val invalid = withoutMessage()

        shouldThrow<IllegalArgumentException> {
            EntityEventRecords.create(entityId, invalid)
        }
    }

    @Test
    fun `reject an event with a blank identifier`() {
        val invalid = withBlankId()

        shouldThrow<IllegalArgumentException> {
            EntityEventRecords.create(entityId, invalid)
        }
    }

    private fun newEvent(): Event =
        eventFactory.createEvent(Sample.messageOfType(StgProjectCreated::class.java))

    private fun withoutContext(): @NonValidated Event {
        val valid = newEvent()
        return Event.newBuilder()
            .setId(valid.id)
            .setMessage(valid.message)
            .buildPartial()
    }

    private fun withoutMessage(): @NonValidated Event {
        val valid = newEvent()
        return Event.newBuilder()
            .setId(valid.id)
            .setContext(valid.context)
            .buildPartial()
    }

    private fun withBlankId(): @NonValidated Event {
        val valid = newEvent()
        return valid.toBuilder()
            .setId(EventId.getDefaultInstance())
            .buildPartial()
    }

    private companion object {

        private val eventFactory =
            TestEventFactory.newInstance(EntityEventRecordsSpec::class.java)
    }
}
