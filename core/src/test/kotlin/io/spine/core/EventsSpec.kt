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
import io.kotest.matchers.shouldNotBe
import io.spine.base.EventMessage
import io.spine.core.Events.checkValid
import io.spine.core.Events.ensureMessage
import io.spine.core.Events.generateId
import io.spine.core.Events.idStringifier
import io.spine.core.Events.nothing
import io.spine.core.Events.toExternal
import io.spine.protobuf.AnyPacker
import io.spine.test.core.projectCreated
import io.spine.test.core.projectId
import io.spine.testing.UtilityClassTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.spine.validation.NonValidated

@DisplayName("`Events` utility should")
internal class EventsSpec : UtilityClassTest<Events>(Events::class.java) {

    @Test
    fun `generate a non-blank event ID`() {
        val id = generateId()
        id.value.isNotBlank() shouldBe true
    }

    @Test
    fun `generate unique event IDs`() {
        generateId() shouldNotBe generateId()
    }

    @Nested
    inner class `ensure event message` {

        @Test
        fun `returning enclosed message from an 'Event'`() {
            val event = stubEvent()
            val message = ensureMessage(event)
            message shouldNotBe null
        }

        @Test
        fun `returning message from 'Any'`() {
            val project = projectId { id = "any-test" }
            val created = projectCreated { projectId = project }
            val packed = AnyPacker.pack(created)
            val result = ensureMessage(packed)
            result shouldBe created
        }
    }

    @Test
    fun `return a stringifier for event IDs`() {
        idStringifier() shouldNotBe null
    }

    @Test
    fun `round-trip event ID through stringifier`() {
        val id = generateId()
        val str = idStringifier().convert(id)
        val restored = idStringifier().reverse().convert(str)
        restored shouldBe id
    }

    @Suppress("DEPRECATION")
    @Test
    fun `validate a non-default event ID`() {
        val id = generateId()
        checkValid(id) shouldBe id
    }

    @Test
    fun `return empty iterable from 'nothing()'`() {
        val result: Iterable<EventMessage> = nothing()
        result.toList().isEmpty() shouldBe true
    }

    @Nested
    inner class `mark events as external` {

        @Test
        fun `marking a single event`() {
            val event = stubEvent()
            event.context.external shouldBe false
            val external = toExternal(event)
            external.context.external shouldBe true
        }

        @Test
        fun `marking a list of events`() {
            val events = listOf(stubEvent(), stubEvent())
            val externals = toExternal(events)
            externals.size shouldBe 2
            externals.forEach { it.context.external shouldBe true }
        }

        @Test
        fun `preserving order when marking a list`() {
            val first = stubEvent()
            val second = stubEvent()
            val events = listOf(first, second)
            val externals = toExternal(events)
            externals[0].id shouldBe first.id
            externals[1].id shouldBe second.id
        }
    }

    private fun stubEvent(): @NonValidated Event {
        val project = projectId { id = javaClass.name }
        val message = projectCreated { projectId = project }
        return Event.newBuilder()
            .setId(generateId())
            .setMessage(AnyPacker.pack(message))
            .setContext(EventContext.newBuilder().buildPartial())
            .buildPartial()
    }
}
