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
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
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
        var requestedDepth = 0
        history.useLoader(object : StateHistoryLoader {
            override fun load(depth: Int): Iterator<EntityRecord> {
                requestedDepth = depth
                return records.reversed().iterator()
            }

            override fun stateAt(at: Timestamp): EntityRecord? = null
        })

        val read = history.read(7).asSequence().toList()

        read shouldContainExactly states.reversed()
        requestedDepth shouldBe 7
    }

    @Test
    fun `answer the state at a time through the installed loader`() {
        val state = newState()
        val retained = record(state, number = 1)
        history.useLoader(object : StateHistoryLoader {
            override fun load(depth: Int): Iterator<EntityRecord> =
                emptyList<EntityRecord>().iterator()

            override fun stateAt(at: Timestamp): EntityRecord? = retained
        })

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

    private fun newState(): StgProject = Sample.messageOfType(StgProject::class.java)

    private fun record(of: StgProject, number: Int): EntityRecord = entityRecord {
        entityId = Identifier.pack(of.id)
        state = AnyPacker.pack(of)
        version = Versions.newVersion(number, currentTime())
    }
}
