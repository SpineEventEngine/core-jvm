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

// https://github.com/protocolbuffers/protobuf
@Suppress(
    "MemberVisibilityCanBePrivate" /* used directly from the outside */,
    "ConstPropertyName" /* https://bit.ly/kotlin-prop-names */
)
object Protobuf {
    const val group = "com.google.protobuf"
    const val version = "4.36.0"

    /**
     * The Java library with Protobuf data types.
     */
    const val javaLib = "$group:protobuf-java:$version"

    /**
     * The Java library with utilities for Protobuf messages, such as `JsonFormat`.
     */
    const val javaUtil = "$group:protobuf-java-util:$version"

    /**
     * The Kotlin library with extensions for Protobuf messages.
     */
    const val kotlin = "$group:protobuf-kotlin:$version"

    /**
     * The Java library containing proto definitions of Google Protobuf types.
     */
    @Suppress("unused")
    const val protoSrcLib = javaLib

    /**
     * All Java and Kotlin libraries we depend on.
     */
    val libs = listOf(
        javaLib,
        javaUtil,
        kotlin
    )
    const val compiler = "$group:protoc:$version"

    // https://github.com/google/protobuf-gradle-plugin/releases
    object GradlePlugin {
        /**
         * The version of this plugin is already specified in the `buildSrc/build.gradle.kts` file.
         * Thus, when applying the plugin to project build files, only the [id] should be used.
         *
         * When changing the version, also change the version used in the `build.gradle.kts`.
         */
        const val version = "0.10.0"
        const val id = "com.google.protobuf"
        const val lib = "$group:protobuf-gradle-plugin:$version"
    }
}
