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

import io.spine.dependency.Dependency
import io.spine.dependency.DependencyWithBom

// https://github.com/JetBrains/kotlin
// https://github.com/Kotlin
@Suppress("unused")
object Kotlin : DependencyWithBom() {

    /**
     * This is the version of Kotlin we use for writing code which does not
     * depend on Gradle and the version of embedded Kotlin.
     */
    @Suppress("MemberVisibilityCanBePrivate") // used directly from the outside.
    const val runtimeVersion = "2.2.21"

    override val version = runtimeVersion
    override val group = "org.jetbrains.kotlin"
    override val bom = "$group:kotlin-bom:$runtimeVersion"

    /**
     * This is the version of
     * [Kotlin embedded into Gradle](https://docs.gradle.org/current/userguide/compatibility.html#kotlin).
     */
    const val embeddedVersion = "2.2.21"

    /**
     * The version of the JetBrains annotations library, which is a transitive
     * dependency for us via Kotlin libraries.
     *
     * @see <a href="https://github.com/JetBrains/java-annotations">Java Annotations</a>
     */
    private const val annotationsVersion = "26.0.2"

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

        override val modules = listOf(api, lib, model)
    }
}
