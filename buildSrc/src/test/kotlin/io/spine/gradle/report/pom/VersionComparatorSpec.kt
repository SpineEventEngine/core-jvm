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

package io.spine.gradle.report.pom

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`VersionComparator` should")
internal class VersionComparatorSpec {

    /**
     * Asserts that [newer] compares above [older], checking both directions.
     */
    private fun assertNewer(newer: String, older: String) {
        VersionComparator.compare(newer, older) shouldBeGreaterThan 0
        VersionComparator.compare(older, newer) shouldBeLessThan 0
    }

    @Test
    fun `compare numeric segments as numbers`() {
        assertNewer("10.0.0", "9.2.0")
        assertNewer("2.10.0", "2.9.1")
        assertNewer("1.0.10", "1.0.9")
    }

    @Test
    fun `compare numeric qualifier segments as numbers`() {
        assertNewer("2.0.0-SNAPSHOT.100", "2.0.0-SNAPSHOT.99")
        assertNewer("2.0.0-SNAPSHOT.100", "2.0.0-SNAPSHOT.070")
    }

    @Test
    fun `treat a release as newer than its pre-release`() {
        assertNewer("2.0.0", "2.0.0-SNAPSHOT.100")
        assertNewer("1.0.0", "1.0.0-RC.2")
    }

    @Test
    fun `treat a longer version as newer when the common segments are equal`() {
        assertNewer("1.0.1", "1.0")
        assertNewer("1.0.0-RC.1", "1.0.0-RC")
    }

    @Test
    fun `ignore the case of textual segments`() {
        assertNewer("1.0.0-snapshot.10", "1.0.0-SNAPSHOT.2")
        VersionComparator.compare("1.0.0-RC", "1.0.0-rc") shouldBe 0
    }

    @Test
    fun `order a numeric segment before a textual one`() {
        assertNewer("1.0.0-alpha", "1.0.0-1")
    }

    @Test
    fun `treat equal versions as equal`() {
        VersionComparator.compare("2.0.0-SNAPSHOT.070", "2.0.0-SNAPSHOT.070") shouldBe 0
        VersionComparator.compare("31.1-jre", "31.1-jre") shouldBe 0
    }
}
