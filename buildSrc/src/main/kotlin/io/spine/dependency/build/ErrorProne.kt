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

// https://errorprone.info/
@Suppress("unused", "ConstPropertyName")
object ErrorProne {
    /**
     * This is the last version that is compatible with Java 17.
     *
     * The version 2.43.0 requires JDK 21.
     * https://github.com/google/error-prone/releases/tag/v2.43.0
     */
    private const val version = "2.42.0"

    const val group = "com.google.errorprone"

    // https://github.com/tbroyer/gradle-errorprone-plugin/blob/v0.8/build.gradle.kts
    private const val javacPluginVersion = "9+181-r4173-1"

    val annotations = listOf(
        "$group:error_prone_annotations:$version",
        "$group:error_prone_type_annotations:$version"
    )
    const val core = "$group:error_prone_core:$version"
    const val checkApi = "$group:error_prone_check_api:$version"
    const val testHelpers = "$group:error_prone_test_helpers:$version"
    const val javacPlugin  = "$group:javac:$javacPluginVersion"

    // https://github.com/tbroyer/gradle-errorprone-plugin/releases
    object GradlePlugin {
        const val id = "net.ltgt.errorprone"
        /**
         * The version of this plugin is already specified in `buildSrc/build.gradle.kts` file.
         * Thus, when applying the plugin to project build files, only the [id] should be used.
         *
         * When the plugin is used as a library (e.g., in tools), its version and the library
         * artifacts are of importance.
         */
        const val version = "5.1.1"
        const val lib = "net.ltgt.gradle:gradle-errorprone-plugin:$version"
    }
}
