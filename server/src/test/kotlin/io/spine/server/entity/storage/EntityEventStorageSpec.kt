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

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.add
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.core.Event
import io.spine.core.Versions.increment
import io.spine.core.Versions.zero
import io.spine.server.ContextSpec
import io.spine.server.entity.EntityEventRecord
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.storage.event.StgProjectCreated
import io.spine.testdata.Sample
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`EntityEventStorage` should")
internal class EntityEventStorageSpec {

    private val entityId = "journaled-entity"
    private lateinit var storage: EntityEventStorage
    private var version = zero()

    @BeforeEach
    fun createStorage() {
        val factory = InMemoryStorageFactory.newInstance()
        val context = ContextSpec.singleTenant("`EntityEventStorage` tests")
        storage = factory.createEntityEventStorage(context)
        version = zero()
    }

    @AfterEach
    fun closeStorage() {
        if (storage.isOpen) {
            storage.close()
        }
    }

    @Test
    fun `provide an empty history for an unknown entity`() {
        val records = storage.historyBackward(entityId, Int.MAX_VALUE)

        records.asSequence().toList().shouldBeEmpty()
    }

    @Nested inner class
    `read the journaled events` {

        @Test
        fun `newest first`() {
            val written = appendEvents(count = 5)

            val read = storage.historyBackward(entityId, Int.MAX_VALUE)

            read.events() shouldContainExactly written.reversed()
        }

        @Test
        fun `limiting the window to the requested batch size`() {
            val written = appendEvents(count = 5)

            val read = storage.historyBackward(entityId, batchSize = 2)

            read.events() shouldContainExactly listOf(written[4], written[3])
        }

        @Test
        fun `only below the given starting version`() {
            val written = appendEvents(count = 5)
            val versionOfThird = written[2].context()
                .version

            val read = storage.historyBackward(
                entityId,
                batchSize = Int.MAX_VALUE,
                startingFrom = versionOfThird
            )

            read.events() shouldContainExactly listOf(written[1], written[0])
        }

        @Test
        fun `emitted only by the entity with the given identifier`() {
            val written = appendEvents(count = 2)
            appendEvents(count = 3, toEntity = "another-entity")

            val read = storage.historyBackward(entityId, Int.MAX_VALUE)

            read.events() shouldContainExactly written.reversed()
        }
    }

    @Nested inner class
    `support journal maintenance by` {

        @Test
        fun `deleting a record by its identifier`() {
            val written = appendEvents(count = 2)
            val newest = storage.historyBackward(entityId, Int.MAX_VALUE).next()

            storage.delete(newest.id) shouldBe true

            val remaining = storage.historyBackward(entityId, Int.MAX_VALUE)
            remaining.events() shouldContainExactly listOf(written[0])
            storage.delete(newest.id) shouldBe false
        }

        @Test
        fun `deleting several records at once`() {
            appendEvents(count = 3)
            val ids = storage.historyBackward(entityId, Int.MAX_VALUE)
                .asSequence()
                .map { it.id }
                .toList()

            storage.deleteAll(ids)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .events()
                .shouldBeEmpty()
        }
    }

    @Nested inner class
    `truncate the journal` {

        @Test
        fun `keeping the requested number of the most recent records per entity`() {
            val ours = appendEvents(count = 5)
            val theirs = appendEvents(count = 3, toEntity = "another-entity")

            storage.truncate(2)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .events() shouldContainExactly listOf(ours[4], ours[3])
            storage.historyBackward("another-entity", Int.MAX_VALUE)
                .events() shouldContainExactly listOf(theirs[2], theirs[1])
        }

        @Test
        fun `doing nothing when the journal is within the kept window`() {
            val written = appendEvents(count = 3)

            storage.truncate(10)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .events() shouldContainExactly written.reversed()
        }

        @Test
        fun `purging all the records older than the given time`() {
            val longAgo = subtract(currentTime(), Durations.fromDays(365))
            val old = appendEvents(count = 2, at = longAgo)
            val recent = appendEvents(count = 2)
            val cutoff = subtract(currentTime(), Durations.fromDays(30))

            storage.truncate(0, cutoff)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .events() shouldContainExactly recent.reversed()
        }

        @Test
        fun `keeping the recent window even when its records are older than the given time`() {
            val longAgo = subtract(currentTime(), Durations.fromDays(365))
            val written = appendEvents(count = 4, at = longAgo)
            val futureCutoff = add(currentTime(), Durations.fromDays(1))

            storage.truncate(2, futureCutoff)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .events() shouldContainExactly listOf(written[3], written[2])
        }

        @Test
        fun `rejecting a negative window`() {
            shouldThrow<IllegalArgumentException> {
                storage.truncate(-1)
            }
            shouldThrow<IllegalArgumentException> {
                storage.truncate(-1, currentTime())
            }
        }
    }

    /**
     * Appends the given number of events, with sequentially growing versions,
     * to the journal of the entity with the given identifier.
     *
     * The versions continue growing across the calls within one test, so that
     * the batches appended later are the more recent ones.
     *
     * @return The appended events in the order of their versions.
     */
    private fun appendEvents(
        count: Int,
        toEntity: String = entityId,
        at: Timestamp? = null
    ): List<Event> {
        val events = List(count) {
            version = increment(version)
            val message = Sample.messageOfType(StgProjectCreated::class.java)
            if (at != null) {
                eventFactory.createEvent(message, version, at)
            } else {
                eventFactory.createEvent(message, version)
            }
        }
        events.forEach {
            storage.write(EntityEventRecords.create(toEntity, it))
        }
        return events
    }

    private fun Iterator<EntityEventRecord>.events(): List<Event> =
        asSequence().map { it.event }
            .toList()

    private companion object {

        private val eventFactory =
            TestEventFactory.newInstance(EntityEventStorageSpec::class.java)
    }
}
