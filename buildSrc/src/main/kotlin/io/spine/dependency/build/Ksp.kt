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

package io.spine.dependency.build

import io.spine.dependency.Dependency

/**
 * Kotlin Symbol Processing API.
 *
 * @see <a href="https://github.com/google/ksp">KSP GitHub repository</a>
 */
@Suppress("unused")
object Ksp : Dependency() {
    override val version = "2.1.21-2.0.1"
    override val group = "com.google.devtools.ksp"

    const val id = "com.google.devtools.ksp"
    const val gradlePluginArtifactName = "com.google.devtools.ksp.gradle.plugin"

    val symbolProcessingApi = "$group:symbol-processing-api"
    val symbolProcessing = "$group:symbol-processing"
    val symbolProcessingAaEmb = "$group:symbol-processing-aa-embeddable"
    val symbolProcessingCommonDeps = "$group:symbol-processing-common-deps"
    val gradlePlugin = "$group:symbol-processing-gradle-plugin"

    override val modules = listOf(
        symbolProcessingApi,
        symbolProcessing,
        symbolProcessingAaEmb,
        symbolProcessingCommonDeps,
        gradlePlugin,
    )
}
