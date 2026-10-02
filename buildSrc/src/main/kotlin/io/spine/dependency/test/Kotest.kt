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

@file:Suppress("unused")

package io.spine.dependency.test

/**
 * Testing framework for Kotlin.
 *
 * @see <a href="https://kotest.io/">Kotest site</a>
 */
@Suppress("unused", "ConstPropertyName")
object Kotest {
    const val version = "6.2.4"
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
