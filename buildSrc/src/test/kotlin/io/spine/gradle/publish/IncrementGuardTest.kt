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

package io.spine.gradle.publish

import io.kotest.matchers.shouldBe
import io.spine.gradle.publish.IncrementGuard.Companion.shouldCheckVersion
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`IncrementGuard` should")
class IncrementGuardTest {

    @Nested
    inner class `require the version check` {

        @Test
        fun `for pull requests targeting default branches`() {
            shouldCheckVersion("pull_request", "master") shouldBe true
            shouldCheckVersion("pull_request", "main") shouldBe true
        }

        @Test
        fun `for pull requests targeting release-line branches`() {
            shouldCheckVersion("pull_request", "2.x-jdk8-master") shouldBe true
            shouldCheckVersion("pull_request", "2.x-jdk8-main") shouldBe true
        }
    }

    @Nested
    inner class `not require the version check` {

        @Test
        fun `for pull requests targeting auxiliary branches`() {
            shouldCheckVersion("pull_request", "epic-feature") shouldBe false
            shouldCheckVersion("pull_request", "master-fixes") shouldBe false
        }

        @Test
        fun `for push events`() {
            shouldCheckVersion("push", "master") shouldBe false
            shouldCheckVersion("push", null) shouldBe false
        }

        @Test
        fun `for pull request events without a base branch`() {
            shouldCheckVersion("pull_request", null) shouldBe false
        }

        @Test
        fun `outside GitHub Actions`() {
            shouldCheckVersion(null, null) shouldBe false
        }
    }
}
