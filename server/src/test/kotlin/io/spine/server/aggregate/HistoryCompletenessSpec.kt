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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.spine.core.Event
import io.spine.core.Version
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`HistoryCompleteness` should")
internal class HistoryCompletenessSpec {

    @Nested
    inner class `accept a history that` {

        @Test
        fun `is complete when read from the beginning`() {
            shouldNotThrowAny {
                HistoryCompleteness.check(ID, history(eventCount = 3), version(3))
            }
        }

        @Test
        fun `is complete when it starts from a snapshot`() {
            shouldNotThrowAny {
                HistoryCompleteness.check(
                    ID, history(eventCount = 2, snapshotVersion = 5), version(7)
                )
            }
        }

        @Test
        fun `has more events than the stored state version implies`() {
            // The stored state version may lag the events after a partial write;
            // an over-complete history must not be rejected.
            shouldNotThrowAny {
                HistoryCompleteness.check(ID, history(eventCount = 3), version(2))
            }
        }

        @Test
        fun `starts from a snapshot newer than a lagging stored state version`() {
            // A partial write can leave the state version trailing the snapshot the read
            // starts from; the expected count must clamp to zero rather than go negative.
            shouldNotThrowAny {
                HistoryCompleteness.check(
                    ID, history(eventCount = 0, snapshotVersion = 5), version(3)
                )
            }
        }
    }

    @Nested
    inner class `skip the check when` {

        @Test
        fun `no authoritative version is available (legacy data)`() {
            shouldNotThrowAny {
                HistoryCompleteness.check(ID, history(eventCount = 1), null)
            }
        }

        @Test
        fun `the aggregate has neither history nor a stored version`() {
            shouldNotThrowAny {
                HistoryCompleteness.check(ID, null, null)
            }
        }

        @Test
        fun `the stored version is zero and there is no history`() {
            shouldNotThrowAny {
                HistoryCompleteness.check(ID, null, version(0))
            }
        }
    }

    @Nested
    inner class `reject a history that` {

        @Test
        fun `is missing the newest event (tail loss)`() {
            // The scenario from issue #838: the newest stored event is invisible on read.
            shouldThrow<IncompleteHistoryException> {
                HistoryCompleteness.check(ID, history(eventCount = 1), version(2))
            }
        }

        @Test
        fun `is missing an event from the middle`() {
            shouldThrow<IncompleteHistoryException> {
                HistoryCompleteness.check(ID, history(eventCount = 2), version(3))
            }
        }

        @Test
        fun `is missing events after the snapshot`() {
            shouldThrow<IncompleteHistoryException> {
                HistoryCompleteness.check(
                    ID, history(eventCount = 1, snapshotVersion = 5), version(7)
                )
            }
        }

        @Test
        fun `is entirely invisible while the state says the aggregate exists`() {
            shouldThrow<IncompleteHistoryException> {
                HistoryCompleteness.check(ID, null, version(3))
            }
        }
    }

    companion object {

        private const val ID = "test-aggregate-id"

        /**
         * Builds a [Version] with the given [number].
         *
         * Uses `buildPartial()` so that the fixture does not need to satisfy the
         * `timestamp` constraint of `spine.core.Version`; the completeness check only
         * reads the version number.
         */
        private fun version(number: Int): Version =
            Version.newBuilder()
                .setNumber(number)
                .buildPartial()

        private fun history(eventCount: Int, snapshotVersion: Int? = null): AggregateHistory {
            val builder = AggregateHistory.newBuilder()
            repeat(eventCount) {
                builder.addEvent(Event.getDefaultInstance())
            }
            if (snapshotVersion != null) {
                builder.setSnapshot(
                    Snapshot.newBuilder()
                        .setVersion(version(snapshotVersion))
                        .buildPartial()
                )
            }
            return builder.buildPartial()
        }
    }
}
