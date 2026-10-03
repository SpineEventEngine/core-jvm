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

package io.spine.dependency.lib

import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Kotlin` should")
internal class KotlinSpec {

    @Test
    fun `give the coordinates of a Gradle plugin module with a single version`() {
        with(Kotlin.GradlePlugin) {
            artifacts.values.forAll {
                it.split(':') shouldHaveSize 3
                it shouldEndWith ":$version"
            }
        }
    }

    @Test
    fun `give the Gradle plugin artifacts declared by 'api', 'lib', and 'model'`() {
        with(Kotlin.GradlePlugin) {
            artifacts.values shouldContainExactly listOf(api, lib, model)
        }
    }
}
