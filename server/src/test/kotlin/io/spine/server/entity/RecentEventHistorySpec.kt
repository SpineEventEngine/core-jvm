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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.core.Enrichment
import io.spine.core.Event
import io.spine.core.Version
import io.spine.core.Versions
import io.spine.test.storage.event.StgProjectCreated
import io.spine.testdata.Sample
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RecentEventHistory` should")
internal class RecentEventHistorySpec {

    private val history = RecentEventHistory()

    @Test
    fun `serve the reads through the installed loader`() {
        val journal = StubJournal(newEvent(version = 1), newEvent(version = 2))
        history.useLoader(journal)

        val read = history.read(7).asSequence().toList()

        read shouldContainExactly journal.events
        journal.lastDepth shouldBe 7
        journal.lastStartingFrom shouldBe null
    }

    @Test
    fun `return no events when no loader is installed`() {
        history.read(Int.MAX_VALUE)
            .asSequence()
            .toList()
            .shouldBeEmpty()
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
    fun `cache loaded events and stop querying an exhausted journal`() {
        val journal = StubJournal(newEvent(version = 1), newEvent(version = 2))
        history.useLoader(journal)

        val first = history.read(5).asSequence().toList()
        val second = history.read(5).asSequence().toList()
        val shallow = history.read(2).asSequence().toList()

        first shouldContainExactly journal.events
        second shouldContainExactly journal.events
        shallow shouldContainExactly journal.events
        journal.calls shouldBe 1
    }

    @Test
    fun `continue a deeper read below the cached events`() {
        val e1 = newEvent(version = 1)
        val e2 = newEvent(version = 2)
        val e3 = newEvent(version = 3)
        val journal = StubJournal(e1, e2, e3)
        history.useLoader(journal)
        history.read(2).asSequence().toList() shouldContainExactly listOf(e3, e2)

        val deeper = history.read(3).asSequence().toList()

        deeper shouldContainExactly listOf(e3, e2, e1)
        journal.calls shouldBe 2
        // Three events requested, one served from the cache.
        journal.lastDepth shouldBe 2
        journal.lastStartingFrom shouldBe e3.context.version
    }

    @Test
    fun `not split a same-version group between the cache and the storage`() {
        val eC = newEvent(version = 4)
        // One dispatch: two events under one version, `eB` newer by time.
        val eA = newEvent(version = 5)
        val eB = newEvent(version = 5)
        val journal = StubJournal(eC, eA, eB)
        history.useLoader(journal)

        val shallow = history.read(2).asSequence().toList()
        val full = history.read(3).asSequence().toList()
        val again = history.read(3).asSequence().toList()

        shallow shouldContainExactly listOf(eB, eA)
        full shouldContainExactly listOf(eB, eA, eC)
        again shouldContainExactly listOf(eB, eA, eC)
        // The continuation resumes from the oldest cached item — a group
        // boundary — so `eA` is never lost to the exclusive version
        // filter of the storage.
        journal.lastStartingFrom shouldBe eA.context.version
    }

    @Test
    fun `serve appended events before the stored ones`() {
        val e1 = newEvent(version = 1)
        val e2 = newEvent(version = 2)
        val journal = StubJournal(e1)
        history.useLoader(journal)
        history.append(e2)

        val read = history.read(3).asSequence().toList()
        val again = history.read(3).asSequence().toList()

        read shouldContainExactly listOf(e2, e1)
        again shouldContainExactly listOf(e2, e1)
        journal.calls shouldBe 1
        // Three events requested, the appended one served from the cache.
        journal.lastDepth shouldBe 2
        journal.lastStartingFrom shouldBe e2.context.version
    }

    @Test
    fun `serve appended events without any loader`() {
        val e1 = newEvent(version = 1)
        val e2 = newEvent(version = 2)

        history.append(e1)
        history.append(e2)

        history.read(5).asSequence().toList() shouldContainExactly listOf(e2, e1)
    }

    @Test
    fun `serve a uniform appended group newest first`() {
        val eA = newEvent(version = 5)
        val eB = newEvent(version = 5)

        history.append(listOf(eA, eB))

        history.read(5).asSequence().toList() shouldContainExactly listOf(eB, eA)
    }

    @Test
    fun `drop the cache when an appended group mixes versions`() {
        val e1 = newEvent(version = 1)
        val journal = StubJournal(e1)
        history.useLoader(journal)
        history.read(5).asSequence().toList()

        history.append(listOf(newEvent(version = 2), newEvent(version = 3)))

        val read = history.read(5).asSequence().toList()
        read shouldContainExactly listOf(e1)
        // The healing also reset `exhausted`, so the storage was consulted again.
        journal.calls shouldBe 2
    }

    @Test
    fun `drop the cache when an appended group does not extend the history`() {
        history.append(newEvent(version = 2))

        history.append(newEvent(version = 2))

        history.read(5)
            .asSequence()
            .toList()
            .shouldBeEmpty()
    }

    @Test
    fun `ignore an empty append`() {
        val e2 = newEvent(version = 2)
        history.append(e2)

        history.append(emptyList())

        history.read(5).asSequence().toList() shouldContainExactly listOf(e2)
    }

    @Test
    fun `clear the enrichments from an appended event`() {
        val enriched = enriched(newEvent(version = 1))
        enriched.context.hasEnrichment() shouldBe true

        history.append(enriched)

        // The cache serves the event enrichment-free, matching the journal,
        // so a read does not depend on whether it is cached or stored.
        val read = history.read(1).asSequence().toList()
        read shouldContainExactly listOf(enriched.clearEnrichments())
        read.single().context.hasEnrichment() shouldBe false
    }

    @Test
    fun `serve a read started before an append from the pre-append window`() {
        val e1 = newEvent(version = 1)
        val e2 = newEvent(version = 2)
        history.useLoader(StubJournal(e1))
        val started = history.read(3)

        history.append(e2)

        started.asSequence().toList() shouldContainExactly listOf(e1)
        history.read(3).asSequence().toList() shouldContainExactly listOf(e2, e1)
    }

    @Test
    fun `keep the cache consistent under interleaved readers`() {
        val e1 = newEvent(version = 1)
        val e2 = newEvent(version = 2)
        val e3 = newEvent(version = 3)
        val journal = StubJournal(e1, e2, e3)
        history.useLoader(journal)
        val one = history.read(3)
        val two = history.read(3)

        val fromOne = mutableListOf<Event>()
        val fromTwo = mutableListOf<Event>()
        repeat(3) {
            fromOne += one.next()
            fromTwo += two.next()
        }

        val expected = listOf(e3, e2, e1)
        fromOne shouldContainExactly expected
        fromTwo shouldContainExactly expected
        history.read(3).asSequence().toList() shouldContainExactly expected
        journal.calls shouldBe 3
    }

    private fun newEvent(version: Int): Event =
        eventFactory.createEvent(
            Sample.messageOfType(StgProjectCreated::class.java),
            Versions.newVersion(version, currentTime())
        )

    private fun enriched(event: Event): Event =
        event.toBuilder()
            .setContext(
                event.context.toBuilder()
                    .setEnrichment(Enrichment.newBuilder().setDoNotEnrich(true))
            )
            .build()

    private companion object {

        private val eventFactory =
            TestEventFactory.newInstance(RecentEventHistorySpec::class.java)
    }
}

/**
 * A loader over an in-memory journal that records how it was called.
 *
 * The events arrive in chronological order and are [served][load]
 * newest first, honoring the exclusive `startingFrom` filter of
 * the real storage.
 */
private class StubJournal(vararg chronological: Event) : EventHistoryLoader {

    /**
     * The journal events, newest first.
     */
    val events: List<Event> = chronological.reversed()

    var calls = 0
        private set

    var lastDepth = 0
        private set

    var lastStartingFrom: Version? = null
        private set

    override fun load(depth: Int, startingFrom: Version?): Iterator<Event> {
        calls++
        lastDepth = depth
        lastStartingFrom = startingFrom
        return events
            .filter { startingFrom == null || it.context.version.number < startingFrom.number }
            .take(depth)
            .iterator()
    }
}
