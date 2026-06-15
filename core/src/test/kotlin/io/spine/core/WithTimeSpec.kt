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

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests `WithTime` via `Version`, which implements the interface through `VersionMixin`.
 */
@DisplayName("`WithTime` should")
internal class WithTimeSpec {

    private fun versionAt(epochSeconds: Long): Version =
        Versions.newVersion(1, Timestamps.fromSeconds(epochSeconds))

    @Nested
    inner class `check 'isAfter'` {

        @Test
        fun `returning true when timestamp is after the reference`() {
            val v = versionAt(200)
            v.isAfter(ts(100)) shouldBe true
        }

        @Test
        fun `returning false when timestamp equals the reference`() {
            val v = versionAt(100)
            v.isAfter(ts(100)) shouldBe false
        }

        @Test
        fun `returning false when timestamp is before the reference`() {
            val v = versionAt(50)
            v.isAfter(ts(100)) shouldBe false
        }
    }

    @Nested
    inner class `check 'isBefore'` {

        @Test
        fun `returning true when timestamp is before the reference`() {
            val v = versionAt(50)
            v.isBefore(ts(100)) shouldBe true
        }

        @Test
        fun `returning false when timestamp equals the reference`() {
            val v = versionAt(100)
            v.isBefore(ts(100)) shouldBe false
        }

        @Test
        fun `returning false when timestamp is after the reference`() {
            val v = versionAt(200)
            v.isBefore(ts(100)) shouldBe false
        }
    }

    @Nested
    inner class `check 'isBetween'` {

        @Test
        fun `returning true when timestamp is strictly within the period`() {
            val v = versionAt(150)
            v.isBetween(ts(100), ts(200)) shouldBe true
        }

        @Test
        fun `returning true when timestamp equals the end bound (inclusive)`() {
            val v = versionAt(200)
            v.isBetween(ts(100), ts(200)) shouldBe true
        }

        @Test
        fun `returning false when timestamp equals the start bound (exclusive)`() {
            val v = versionAt(100)
            v.isBetween(ts(100), ts(200)) shouldBe false
        }

        @Test
        fun `returning false when timestamp is before the period`() {
            val v = versionAt(50)
            v.isBetween(ts(100), ts(200)) shouldBe false
        }
    }

    @Test
    fun `convert to 'Instant'`() {
        val v = versionAt(1_000_000)
        val instant = v.instant()
        instant shouldNotBe null
        instant.epochSecond shouldBe 1_000_000
    }

    private fun ts(epochSeconds: Long): Timestamp = Timestamps.fromSeconds(epochSeconds)
}
