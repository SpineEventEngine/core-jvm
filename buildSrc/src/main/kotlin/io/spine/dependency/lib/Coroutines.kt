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

/**
 * Kotlin Coroutines.
 *
 * @see <a href="https://github.com/Kotlin/kotlinx.coroutines">GitHub project</a>
 */
@Suppress("unused", "ConstPropertyName")
@Deprecated(
    message = "Please use `Coroutines` from the `io.spine.dependency.kotlinx` package",
    replaceWith = ReplaceWith(
        expression = "Coroutines",
        imports = ["io.spine.dependency.kotlinx.Coroutines"]
    )
)
object Coroutines {
    const val group = "org.jetbrains.kotlinx"
    val version = io.spine.dependency.kotlinx.Coroutines.version
    val bom = "$group:kotlinx-coroutines-bom:$version"
    val core = "$group:kotlinx-coroutines-core:$version"
    val coreJvm = "$group:kotlinx-coroutines-core-jvm:$version"
    val jdk8 = "$group:kotlinx-coroutines-jdk8:$version"
    val debug = "$group:kotlinx-coroutines-debug:$version"
    val test = "$group:kotlinx-coroutines-test:$version"
    val testJvm = "$group:kotlinx-coroutines-test-jvm:$version"
}
