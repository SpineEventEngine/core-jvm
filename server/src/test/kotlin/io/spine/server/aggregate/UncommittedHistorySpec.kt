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

package io.spine.server.aggregate

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.server.aggregate.given.Given.EventMessage.taskAdded
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.event
import io.spine.server.type.EventEnvelope
import io.spine.test.aggregate.ProjectId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`UncommittedHistory` should")
internal class UncommittedHistorySpec {

    @Test
    fun `keep the events of a successful batch`() {
        val history = newHistory()

        history.startTracking(SNAPSHOT_TRIGGER)
        history.track(anEvent(1))
        history.track(anEvent(2))
        history.stopTracking(/* successful = */ true)

        history.events().list() shouldHaveSize 2
    }

    @Test
    fun `discard the events tracked during a failed batch`() {
        val history = newHistory()

        history.startTracking(SNAPSHOT_TRIGGER)
        history.track(anEvent(1))
        history.track(anEvent(2))
        history.stopTracking(/* successful = */ false)

        history.hasEvents() shouldBe false
    }

    @Test
    fun `discard only the failed batch, keeping earlier ones`() {
        val history = newHistory()

        history.startTracking(SNAPSHOT_TRIGGER)
        history.track(anEvent(1))
        history.stopTracking(/* successful = */ true)

        history.startTracking(SNAPSHOT_TRIGGER)
        history.track(anEvent(2))
        history.track(anEvent(3))
        history.stopTracking(/* successful = */ false)

        history.events().list() shouldHaveSize 1
    }

    @Test
    fun `discard a snapshot segment created during a failed batch`() {
        val history = newHistory()

        // A trigger of `1` makes each tracked event complete a snapshot segment.
        history.startTracking(1)
        history.track(anEvent(1))
        history.track(anEvent(2))
        history.stopTracking(/* successful = */ false)

        history.hasEvents() shouldBe false
    }

    @Test
    fun `keep a snapshot segment of an earlier successful batch when a later one fails`() {
        val history = newHistory()

        history.startTracking(1)
        history.track(anEvent(1))
        history.stopTracking(/* successful = */ true)

        history.startTracking(1)
        history.track(anEvent(2))
        history.stopTracking(/* successful = */ false)

        history.events().list() shouldHaveSize 1
    }

    private companion object {

        const val SNAPSHOT_TRIGGER = 100

        val ID: ProjectId = ProjectId.newBuilder()
            .setUuid("prj-uncommitted-history")
            .build()

        fun newHistory() = UncommittedHistory { Snapshot.getDefaultInstance() }

        /** Wraps an `AggTaskAdded` event of the given version into an envelope. */
        fun anEvent(versionNumber: Int): EventEnvelope =
            EventEnvelope.of(event(taskAdded(ID), versionNumber))
    }
}
