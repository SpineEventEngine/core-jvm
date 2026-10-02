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

package io.spine.dependency.kotlinx

/**
 * The [KotlinX Serialization](https://github.com/Kotlin/kotlinx.serialization) library.
 */
@Suppress("ConstPropertyName") // https://bit.ly/kotlin-prop-names
object Serialization {

    const val group = KotlinX.group

    /**
     * The version of the library.
     *
     * @see <a href="https://github.com/Kotlin/kotlinx.serialization/releases">Releases</a>
     */
    const val version = "1.11.0"

    private const val infix = "kotlinx-serialization"
    const val bom = "$group:$infix-bom:$version"
    const val coreJvm = "$group:$infix-core-jvm"
    const val json = "$group:$infix-json"

    /**
     * The [Gradle plugin](https://github.com/Kotlin/kotlinx.serialization/tree/master?tab=readme-ov-file#gradle)
     * for using the serialization library.
     *
     * Usage:
     * ```kotlin
     * plugins {
     *     // ...
     *     kotlin(Serialization.GradlePlugin.shortId) version Kotlin.version
     * }
     * ```
     */
    object GradlePlugin {

        /**
         * The ID to be used with the `kotlin(shortId)` DSL under the `plugins { }` block.
         */
        const val shortId = "plugin.serialization"

        /**
         * The full ID of the plugin.
         */
        const val id = "org.jetbrains.kotlin.$shortId"
    }
}
