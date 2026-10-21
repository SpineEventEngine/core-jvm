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

import io.spine.dependency.Dependency
import io.spine.dependency.DependencyWithBom

// https://github.com/JetBrains/kotlin
// https://github.com/Kotlin
@Suppress("unused")
object Kotlin : DependencyWithBom() {

    /**
     * This is the version of Kotlin we use for writing code that does not depend on Gradle.
     */
    @Suppress("MemberVisibilityCanBePrivate") // used directly from the outside.
    const val runtimeVersion = "2.4.10"

    override val version = runtimeVersion
    override val group = "org.jetbrains.kotlin"
    override val bom = "$group:kotlin-bom:$runtimeVersion"

    /**
     * The version of the embeddable Kotlin compiler.
     *
     * This is the version of [Compiler.embeddable], the
     * `org.jetbrains.kotlin:kotlin-compiler-embeddable` artifact that packages
     * the compiler for use inside another application.
     *
     * Despite the name, it is not the version of Kotlin
     * [embedded into Gradle](https://docs.gradle.org/current/userguide/compatibility.html#kotlin),
     * which the Gradle version determines on its own.
     */
    const val embeddedVersion = "2.4.10"

    /**
     * The version of the JetBrains annotations library, which is a transitive
     * dependency for us via Kotlin libraries.
     *
     * @see <a href="https://github.com/JetBrains/java-annotations">Java Annotations</a>
     */
    private const val annotationsVersion = "26.1.0"

    val scriptRuntime = "$group:kotlin-script-runtime:$runtimeVersion"

    object StdLib : Dependency() {
        override val version = runtimeVersion
        override val group = Kotlin.group

        private const val infix = "kotlin-stdlib"
        val itself = "$group:$infix"
        val common = "$group:$infix-common"
        val jdk7 = "$group:$infix-jdk7"
        val jdk8 = "$group:$infix-jdk8"

        override val modules = listOf(itself, common, jdk7, jdk8)
    }

    @Deprecated("Please use `StdLib.itself` instead.", ReplaceWith("StdLib.itself"))
    val stdLib       = StdLib.itself

    @Deprecated("Please use `StdLib.common` instead.", ReplaceWith("StdLib.common"))
    val stdLibCommon = StdLib.common

    @Deprecated("Please use `StdLib.jdk7` instead.", ReplaceWith("StdLib.jdk7"))
    val stdLibJdk7   = StdLib.jdk7

    @Deprecated("Please use `StdLib.jdk8` instead.")
    val stdLibJdk8   = StdLib.jdk8

    val toolingCore = "$group:kotlin-tooling-core"
    val reflect    = "$group:kotlin-reflect"
    val testJUnit5 = "$group:kotlin-test-junit5"

    /**
     * The modules our interest that do not belong to [StdLib].
     */
    override val modules = listOf(reflect, testJUnit5)

    @Deprecated(message = "Please use `GradlePlugin.api` instead.", ReplaceWith("GradlePlugin.api"))
    val gradlePluginApi = "$group:kotlin-gradle-plugin-api"

    @Deprecated(message = "Please use `GradlePlugin.lib` instead.", ReplaceWith("GradlePlugin.lib"))
    val gradlePluginLib = "$group:kotlin-gradle-plugin"

    const val jetbrainsAnnotations = "org.jetbrains:annotations:$annotationsVersion"

    object Compiler {
        val embeddable = "$group:kotlin-compiler-embeddable:$embeddedVersion"
    }

    object GradlePlugin : Dependency() {
        override val version = runtimeVersion
        override val group = Kotlin.group

        val api = "$group:kotlin-gradle-plugin-api:$version"
        val lib = "$group:kotlin-gradle-plugin:$version"
        val model = "$group:kotlin-gradle-model:$version"

        /**
         * The coordinates of [api], [lib], and [model] without the version,
         * which [artifacts] appends to each of them.
         */
        override val modules = listOf(api, lib, model).map { it.substringBeforeLast(':') }
    }
}
