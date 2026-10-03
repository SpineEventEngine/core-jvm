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

package io.spine.dependency.build

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Ksp` should")
internal class KspSpec {

    @Test
    fun `return the coordinates of a module with the version`() {
        assertSoftly(Ksp) {
            symbolProcessingApi() shouldBe "$symbolProcessingApi:$version"
            symbolProcessing() shouldBe "$symbolProcessing:$version"
            symbolProcessingAaEmb() shouldBe "$symbolProcessingAaEmb:$version"
            symbolProcessingCommonDeps() shouldBe "$symbolProcessingCommonDeps:$version"
            gradlePlugin() shouldBe "$gradlePlugin:$version"
        }
    }

    /**
     * Gradle resolves a plugin requested by its ID through the plugin marker
     * `<id>:<id>.gradle.plugin`.
     *
     * See: https://docs.gradle.org/current/userguide/plugins_intermediate.html#sec:plugin_markers
     */
    @Test
    fun `return the coordinates of the plugin marker with the version`() {
        Ksp.gradlePluginMarker() shouldBe "${Ksp.id}:${Ksp.id}.gradle.plugin:${Ksp.version}"
    }
}
