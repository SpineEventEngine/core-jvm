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

package io.spine.server.entity

import com.google.protobuf.Timestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.Version
import io.spine.core.Versions
import io.spine.protobuf.AnyPacker
import io.spine.test.storage.StgProject
import io.spine.testdata.Sample
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RecentStateHistory` should")
internal class RecentStateHistorySpec {

    private val history = RecentStateHistory<StgProject>()

    @Test
    fun `serve the reads through the installed loader, unpacking the states`() {
        val states = List(2) { newState() }
        val records = states.mapIndexed { index, state -> record(state, index + 1) }
        val loader = StubStateHistory(records)
        history.useLoader(loader)

        val read = history.read(7).asSequence().toList()

        read shouldContainExactly states.reversed()
        loader.lastDepth shouldBe 7
        loader.lastStartingFrom shouldBe null
    }

    @Test
    fun `answer the state at a time through the installed loader`() {
        val state = newState()
        val retained = record(state, number = 1)
        history.useLoader(StubStateHistory(emptyList(), atAnswer = retained))

        history.stateAt(currentTime()) shouldBe state
    }

    @Test
    fun `return no states when no loader is installed`() {
        history.read(Int.MAX_VALUE)
            .asSequence()
            .toList()
            .shouldBeEmpty()
        history.stateAt(currentTime()) shouldBe null
    }

    @Test
    fun `reject a non-positive depth`() {
        shouldThrow<IllegalArgumentException> {
            history.read(0)
        }
        shouldThrow<IllegalArgumentException> {
            history.read(-1)
        }
    }

    @Test
    fun `cache loaded states and stop querying an exhausted history`() {
        val states = List(2) { newState() }
        val records = states.mapIndexed { index, state -> record(state, index + 1) }
        val loader = StubStateHistory(records)
        history.useLoader(loader)

        val first = history.read(5).asSequence().toList()
        val second = history.read(5).asSequence().toList()

        first shouldContainExactly states.reversed()
        second shouldContainExactly states.reversed()
        loader.calls shouldBe 1
    }

    @Test
    fun `serve repeated reads with the same unpacked instances`() {
        val records = List(2) { newState() }
            .mapIndexed { index, state -> record(state, index + 1) }
        history.useLoader(StubStateHistory(records))

        val first = history.read(3).asSequence().toList()
        val second = history.read(3).asSequence().toList()

        first[0] shouldBeSameInstanceAs second[0]
        first[1] shouldBeSameInstanceAs second[1]
    }

    @Test
    fun `continue a deeper read below the cached states`() {
        val states = List(3) { newState() }
        val records = states.mapIndexed { index, state -> record(state, index + 1) }
        val loader = StubStateHistory(records)
        history.useLoader(loader)
        history.read(2).asSequence().toList() shouldContainExactly
                listOf(states[2], states[1])

        val deeper = history.read(3).asSequence().toList()

        deeper shouldContainExactly states.reversed()
        loader.calls shouldBe 2
        // Three states requested, one served from the cache.
        loader.lastDepth shouldBe 2
        loader.lastStartingFrom shouldBe records[2].version
    }

    @Test
    fun `serve an appended record before the stored ones`() {
        val older = newState()
        val newer = newState()
        val appended = record(newer, number = 2)
        val loader = StubStateHistory(listOf(record(older, number = 1)))
        history.useLoader(loader)
        history.append(appended)

        val read = history.read(3).asSequence().toList()
        val again = history.read(3).asSequence().toList()

        read shouldContainExactly listOf(newer, older)
        again shouldContainExactly listOf(newer, older)
        loader.calls shouldBe 1
        loader.lastStartingFrom shouldBe appended.version
    }

    @Test
    fun `serve appended records without any loader`() {
        val older = newState()
        val newer = newState()

        history.append(record(older, number = 1))
        history.append(record(newer, number = 2))

        history.read(5).asSequence().toList() shouldContainExactly listOf(newer, older)
    }

    @Test
    fun `drop the cache when an appended record is not newer than the cached ones`() {
        history.append(record(newState(), number = 2))

        history.append(record(newState(), number = 1))

        history.read(5)
            .asSequence()
            .toList()
            .shouldBeEmpty()
    }

    @Test
    fun `fail fast at read time when the loader rejects the reading`() {
        history.useLoader(object : StateHistoryLoader {
            override fun load(depth: Int, startingFrom: Version?): Iterator<EntityRecord> =
                error("The state history is not recorded.")

            override fun stateAt(at: Timestamp): EntityRecord? = null
        })

        shouldThrow<IllegalStateException> {
            history.read(5)
        }
    }

    private fun newState(): StgProject = Sample.messageOfType(StgProject::class.java)

    private fun record(of: StgProject, number: Int): EntityRecord = entityRecord {
        entityId = Identifier.pack(of.id)
        state = AnyPacker.pack(of)
        version = Versions.newVersion(number, currentTime())
    }
}

/**
 * A loader over an in-memory state history which records how it was called.
 *
 * The records arrive in the chronological order and are [served][load]
 * newest first, honoring the strict-less `startingFrom` filter of
 * the real storage.
 */
private class StubStateHistory(
    chronological: List<EntityRecord>,
    private val atAnswer: EntityRecord? = null
) : StateHistoryLoader {

    /**
     * The stored records, newest first.
     */
    private val records: List<EntityRecord> = chronological.reversed()

    var calls = 0
        private set

    var lastDepth = 0
        private set

    var lastStartingFrom: Version? = null
        private set

    override fun load(depth: Int, startingFrom: Version?): Iterator<EntityRecord> {
        calls++
        lastDepth = depth
        lastStartingFrom = startingFrom
        return records
            .filter { startingFrom == null || it.version.number < startingFrom.number }
            .take(depth)
            .iterator()
    }

    override fun stateAt(at: Timestamp): EntityRecord? = atAnswer
}
