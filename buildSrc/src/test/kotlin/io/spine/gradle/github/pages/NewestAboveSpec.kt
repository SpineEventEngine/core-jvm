/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.gradle.github.pages

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies how the publication of the API documentation decides whether the most
 * recent documentation of a module may be replaced by that of the published version.
 */
@DisplayName("`newestAbove` should")
internal class NewestAboveSpec {

    @Test
    fun `find no newer version when nothing is published`() {
        emptyList<String>().newestAbove("2.0.0").shouldBeNull()
    }

    @Test
    fun `find no newer version for the newest one`() {
        listOf("1.9.0", "2.0.0-SNAPSHOT.575", "2.0.0-SNAPSHOT.99")
            .newestAbove("2.0.0").shouldBeNull()
    }

    @Test
    fun `find no newer version for a version published again`() {
        listOf("1.9.0", "2.0.0").newestAbove("2.0.0").shouldBeNull()
    }

    @Test
    fun `find the newest version above an older one`() {
        listOf("1.9.0", "2.1.0", "2.0.0").newestAbove("1.9.5") shouldBe "2.1.0"
    }

    @Test
    fun `compare versions by their meaning rather than as text`() {
        listOf("2.9.0").newestAbove("2.10.0").shouldBeNull()
        listOf("2.10.0").newestAbove("2.9.0") shouldBe "2.10.0"
    }
}
