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

@file:Suppress("unused")

package io.spine.dependency.test

/**
 * Testing framework for Kotlin.
 *
 * @see <a href="https://kotest.io/">Kotest site</a>
 */
@Suppress("unused", "ConstPropertyName")
object Kotest {
    const val version = "6.1.11"
    const val group = "io.kotest"
    const val gradlePluginId = "io.kotest"
    const val assertions = "$group:kotest-assertions-core:$version"
    const val runnerJUnit5 = "$group:kotest-runner-junit5:$version"
    const val runnerJUnit5Jvm = "$group:kotest-runner-junit5-jvm:$version"
    const val frameworkEngine = "$group:kotest-framework-engine:$version"
    const val common = "$group:kotest-common:$version"

    /**
     * @deprecated Use `frameworkEngine` instead.
     */
    @Deprecated("Use `frameworkEngine` instead.", ReplaceWith("frameworkEngine"))
    const val frameworkApi = "$group:kotest-framework-api:$version"
    /**
     * @deprecated The dependency was merged into the core framework.
     */
    @Deprecated("The dependency was merged into the core framework.")
    const val datatest = "$group:kotest-framework-datatest:$version"
}
