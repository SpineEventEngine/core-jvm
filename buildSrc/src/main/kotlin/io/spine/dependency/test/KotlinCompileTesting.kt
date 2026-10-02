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

package io.spine.dependency.test

/**
 * A library for in-process compilation of Kotlin and Java code compilation.
 *
 * @see <a href="https://github.com/zacsweers/kotlin-compile-testing">GitHub repo</a>
 */
@Suppress("unused", "ConstPropertyName")
object KotlinCompileTesting {
    private const val version = "0.13.0"
    private const val group = "dev.zacsweers.kctfork"
    const val libCore = "$group:core:$version"
    const val libKsp = "$group:ksp:$version"
}
