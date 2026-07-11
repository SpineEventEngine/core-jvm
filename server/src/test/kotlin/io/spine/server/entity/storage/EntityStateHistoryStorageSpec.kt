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
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.Version
import io.spine.core.Versions
import io.spine.protobuf.AnyPacker
import io.spine.server.ContextSpec
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.entityRecord
import io.spine.server.entity.entityStateId
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.storage.StgProject
import io.spine.testdata.Sample
import io.spine.validation.NonValidated
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`EntityStateHistoryStorage` should")
internal class EntityStateHistoryStorageSpec {

    private val entityId = "state-tracked-entity"
    private val anotherEntity = "another-entity"
    private lateinit var storage: EntityStateHistoryStorage
    private var lastVersion = 0

    @BeforeEach
    fun createStorage() {
        val factory = InMemoryStorageFactory.newInstance()
        val context = ContextSpec.singleTenant("`EntityStateHistoryStorage` tests")
        storage = factory.createEntityStateHistoryStorage(context, StgProject::class.java)
        lastVersion = 0
    }

    @AfterEach
    fun closeStorage() {
        if (::storage.isInitialized && storage.isOpen) {
            storage.close()
        }
    }

    @Test
    fun `provide an empty history for an unknown entity`() {
        val records = storage.historyBackward(entityId, Int.MAX_VALUE)

        records.records().shouldBeEmpty()
    }

    @Test
    fun `reject a non-positive batch size`() {
        shouldThrow<IllegalArgumentException> {
            storage.historyBackward(entityId, batchSize = 0)
        }
        shouldThrow<IllegalArgumentException> {
            storage.historyBackward(entityId, batchSize = -1)
        }
    }

    @Nested inner class
    `store a written record` {

        @Test
        fun `as-is, keyed by its entity and version`() {
            val written = record(number = 1)

            storage.write(written)

            val read = storage.historyBackward(entityId, batchSize = 1).next()
            read shouldBe written
        }

        @Test
        fun `overwriting the record with the same entity and version`() {
            val start = currentTime()
            val first = record(number = 1, at = start)
            val second = record(number = 1, at = add(start, Durations.fromSeconds(10)))

            storage.write(first)
            storage.write(second)

            val read = storage.historyBackward(entityId, Int.MAX_VALUE)
            read.records() shouldContainExactly listOf(second)
        }

        @Test
        fun `rejecting a record without the entity identifier`() {
            val noEntityId = entityRecord {
                state = packedState()
                version = Versions.newVersion(1, currentTime())
            }
            shouldThrow<IllegalArgumentException> {
                storage.write(noEntityId)
            }
        }

        @Test
        fun `rejecting a record without a version`() {
            val noVersion = entityRecord {
                entityId = Identifier.pack(this@EntityStateHistoryStorageSpec.entityId)
                state = packedState()
            }
            shouldThrow<IllegalArgumentException> {
                storage.write(noVersion)
            }
        }

        @Test
        fun `rejecting a record whose version has no timestamp`() {
            val timeless = entityRecord {
                entityId = Identifier.pack(this@EntityStateHistoryStorageSpec.entityId)
                state = packedState()
                version = versionWithoutTimestamp()
            }
            shouldThrow<IllegalArgumentException> {
                storage.write(timeless)
            }
        }

        @Test
        fun `rejecting an identifier that does not match the record`() {
            val written = record(number = 1)
            val mismatching = entityStateId {
                entityId = written.entityId
                version = 42
            }
            shouldThrow<IllegalArgumentException> {
                storage.write(mismatching, written)
            }
        }

        private fun versionWithoutTimestamp(): @NonValidated Version =
            Version.newBuilder()
                .setNumber(1)
                .buildPartial()
    }

    @Nested inner class
    `read the state history` {

        @Test
        fun `newest first`() {
            val written = appendRecords(count = 5)

            val read = storage.historyBackward(entityId, Int.MAX_VALUE)

            read.records() shouldContainExactly written.reversed()
        }

        @Test
        fun `limiting the window to the requested batch size`() {
            val written = appendRecords(count = 5)

            val read = storage.historyBackward(entityId, batchSize = 2)

            read.records() shouldContainExactly listOf(written[4], written[3])
        }

        @Test
        fun `only for the entity with the given identifier`() {
            val written = appendRecords(count = 2)
            appendRecords(count = 3, toEntity = anotherEntity)

            val read = storage.historyBackward(entityId, Int.MAX_VALUE)

            read.records() shouldContainExactly written.reversed()
        }

        @Test
        fun `only below the given starting version`() {
            val written = appendRecords(count = 5)
            val versionOfThird = written[2].version

            val read = storage.historyBackward(
                entityId,
                batchSize = Int.MAX_VALUE,
                startingFrom = versionOfThird
            )

            read.records() shouldContainExactly listOf(written[1], written[0])
        }
    }

    @Nested inner class
    `answer the state at a given time` {

        private val start = currentTime()

        @Test
        fun `with the record effective exactly at the given time`() {
            writeRecord(number = 1, at = at(0))
            val second = writeRecord(number = 2, at = at(10))
            writeRecord(number = 3, at = at(20))

            storage.stateAt(entityId, at(10)) shouldBe second
        }

        @Test
        fun `with the latest record effective before the given time`() {
            writeRecord(number = 1, at = at(0))
            val second = writeRecord(number = 2, at = at(10))
            writeRecord(number = 3, at = at(20))

            storage.stateAt(entityId, at(15)) shouldBe second
        }

        @Test
        fun `with the highest version among the records of the same instant`() {
            writeRecord(number = 1, at = at(0))
            writeRecord(number = 2, at = at(10))
            val third = writeRecord(number = 3, at = at(10))

            storage.stateAt(entityId, at(10)) shouldBe third
        }

        @Test
        fun `with null when the time precedes the oldest retained record`() {
            // The records of the earlier versions are not retained,
            // as if trimmed away by the depth enforcement.
            writeRecord(number = 3, at = at(20))
            writeRecord(number = 4, at = at(30))

            storage.stateAt(entityId, at(10)) shouldBe null
        }

        @Test
        fun `with null for an unknown entity`() {
            storage.stateAt(entityId, currentTime()) shouldBe null
        }

        private fun at(seconds: Long): Timestamp =
            add(start, Durations.fromSeconds(seconds))
    }

    @Nested inner class
    `trim the per-entity history` {

        @Test
        fun `keeping the requested number of the most recent records`() {
            val written = appendRecords(count = 5)

            storage.trim(entityId, keepMostRecent = 2)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly listOf(written[4], written[3])
        }

        @Test
        fun `not touching the records of other entities`() {
            appendRecords(count = 5)
            val theirs = appendRecords(count = 3, toEntity = anotherEntity)

            storage.trim(entityId, keepMostRecent = 2)

            storage.historyBackward(anotherEntity, Int.MAX_VALUE)
                .records() shouldContainExactly theirs.reversed()
        }

        @Test
        fun `doing nothing when the history is within the kept size`() {
            val written = appendRecords(count = 3)

            storage.trim(entityId, keepMostRecent = 10)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly written.reversed()
        }

        @Test
        fun `purging the whole history of the entity when keeping zero`() {
            appendRecords(count = 3)

            storage.trim(entityId, keepMostRecent = 0)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records()
                .shouldBeEmpty()
        }

        @Test
        fun `rejecting a negative window`() {
            shouldThrow<IllegalArgumentException> {
                storage.trim(entityId, keepMostRecent = -1)
            }
        }
    }

    @Nested inner class
    `truncate the history` {

        @Test
        fun `keeping the requested number of the most recent records per entity`() {
            val ours = appendRecords(count = 5)
            val theirs = appendRecords(count = 3, toEntity = anotherEntity)

            storage.truncate(2)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly listOf(ours[4], ours[3])
            storage.historyBackward(anotherEntity, Int.MAX_VALUE)
                .records() shouldContainExactly listOf(theirs[2], theirs[1])
        }

        @Test
        fun `doing nothing when the history is within the kept window`() {
            val written = appendRecords(count = 3)

            storage.truncate(10)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly written.reversed()
        }

        @Test
        fun `purging all the records older than the given time`() {
            val longAgo = subtract(currentTime(), Durations.fromDays(365))
            appendRecords(count = 2, at = longAgo)
            val recent = appendRecords(count = 2)
            val cutoff = subtract(currentTime(), Durations.fromDays(30))

            storage.truncate(0, cutoff)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly recent.reversed()
        }

        @Test
        fun `keeping the recent window even when its records are older than the given time`() {
            val longAgo = subtract(currentTime(), Durations.fromDays(365))
            val written = appendRecords(count = 4, at = longAgo)
            val futureCutoff = add(currentTime(), Durations.fromDays(1))

            storage.truncate(2, futureCutoff)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly listOf(written[3], written[2])
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

    @Nested inner class
    `support history maintenance by` {

        @Test
        fun `deleting a record by its identifier`() {
            val written = appendRecords(count = 2)
            val newestId = entityStateId {
                entityId = Identifier.pack(this@EntityStateHistoryStorageSpec.entityId)
                version = written[1].version.number
            }

            storage.delete(newestId) shouldBe true

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records() shouldContainExactly listOf(written[0])
            storage.delete(newestId) shouldBe false
        }

        @Test
        fun `deleting several records at once`() {
            val written = appendRecords(count = 3)
            val packedId = Identifier.pack(entityId)
            val ids = written.map {
                entityStateId {
                    entityId = packedId
                    version = it.version.number
                }
            }

            storage.deleteAll(ids)

            storage.historyBackward(entityId, Int.MAX_VALUE)
                .records()
                .shouldBeEmpty()
        }
    }

    /**
     * Builds a state record of the entity with the given identifier.
     *
     * The state is a randomly populated [StgProject].
     */
    private fun record(
        entity: String = entityId,
        number: Int,
        at: Timestamp = currentTime()
    ): EntityRecord = entityRecord {
        entityId = Identifier.pack(entity)
        state = packedState()
        version = Versions.newVersion(number, at)
    }

    /**
     * Builds and stores a state record of the entity with the given identifier.
     */
    private fun writeRecord(
        number: Int,
        at: Timestamp,
        entity: String = entityId
    ): EntityRecord {
        val result = record(entity = entity, number = number, at = at)
        storage.write(result)
        return result
    }

    /**
     * Appends the given number of records, with sequentially growing versions,
     * to the history of the entity with the given identifier.
     *
     * The versions continue growing across the calls within one test, so that
     * the batches appended later are the more recent ones.
     *
     * @return The appended records in the order of their versions.
     */
    private fun appendRecords(
        count: Int,
        toEntity: String = entityId,
        at: Timestamp? = null
    ): List<EntityRecord> {
        val records = List(count) {
            lastVersion++
            record(entity = toEntity, number = lastVersion, at = at ?: currentTime())
        }
        records.forEach {
            storage.write(it)
        }
        return records
    }

    private fun Iterator<EntityRecord>.records(): List<EntityRecord> =
        asSequence().toList()

    private companion object {

        /**
         * Packs a randomly populated [StgProject] to serve as a record state.
         */
        private fun packedState(): com.google.protobuf.Any =
            AnyPacker.pack(Sample.messageOfType(StgProject::class.java))
    }
}
