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

import io.spine.dependency.DependencyWithBom

/**
 * Kotlin Coroutines.
 *
 * @see <a href="https://github.com/Kotlin/kotlinx.coroutines">GitHub project</a>
 */
object Coroutines : DependencyWithBom() {
    override val group = KotlinX.group
    override val version = "1.11.0"

    @Suppress("ConstPropertyName") // https://bit.ly/kotlin-prop-names
    const val infix = "kotlinx-coroutines"

    override val bom = "$group:$infix-bom:$version"

    val core = "$group:$infix-core"
    val coreJvm = "$group:$infix-core-jvm"
    val jdk7 = "$group:$infix-jdk7"
    val jdk8 = "$group:$infix-jdk8"
    val debug = "$group:$infix-debug"
    val test = "$group:$infix-test"
    val testJvm = "$group:$infix-test-jvm"

    override val modules = listOf(core, coreJvm, jdk7, jdk8, debug, test, testJvm)
}
