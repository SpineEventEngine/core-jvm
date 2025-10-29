/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.dependency.lib

// https://github.com/protocolbuffers/protobuf
@Suppress(
    "MemberVisibilityCanBePrivate" /* used directly from the outside */,
    "ConstPropertyName" /* https://bit.ly/kotlin-prop-names */
)
object Protobuf {
    const val group = "com.google.protobuf"
    const val version = "4.33.0"

    /**
     * The Java library with Protobuf data types.
     */
    const val javaLib = "$group:protobuf-java:$version"

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
        "$group:protobuf-java-util:$version",
        "$group:protobuf-kotlin:$version"
    )
    const val compiler = "$group:protoc:$version"

    // https://github.com/google/protobuf-gradle-plugin/releases
    object GradlePlugin {
        /**
         * The version of this plugin is already specified in `buildSrc/build.gradle.kts` file.
         * Thus, when applying the plugin to project build files, only the [id] should be used.
         *
         * When changing the version, also change the version used in the `build.gradle.kts`.
         */
        const val version = "0.9.5"
        const val id = "com.google.protobuf"
        const val lib = "$group:protobuf-gradle-plugin:$version"
    }
}
