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
    const val version = "1.8.1"

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
         * The ID to be used with the `kotlin(shortId)` DSL under the`plugins { }` block.
         */
        const val shortId = "plugin.serialization"

        /**
         * The full ID of the plugin.
         */
        const val id = "org.jetbrains.kotlin.$shortId"
    }
}
