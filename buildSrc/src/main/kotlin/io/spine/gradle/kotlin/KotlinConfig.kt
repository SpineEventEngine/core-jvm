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

package io.spine.gradle.kotlin

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Sets [Java toolchain](https://kotlinlang.org/docs/gradle.html#gradle-java-toolchains-support)
 * to the specified version (e.g., 11 or 8).
 */
fun KotlinJvmProjectExtension.applyJvmToolchain(version: Int) {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(version))
    }
}

/**
 * Sets [Java toolchain](https://kotlinlang.org/docs/gradle.html#gradle-java-toolchains-support)
 * to the specified version (e.g. "11" or "8").
 */
@Suppress("unused")
fun KotlinJvmProjectExtension.applyJvmToolchain(version: String) =
    applyJvmToolchain(version.toInt())

/**
 * Opts-in to experimental features that we use in our codebase.
 *
 * One flag is deliberately withheld rather than passed — see the comment on
 * `-Xcontext-parameters` in the body.
 */
@Suppress("unused")
fun KotlinCommonCompilerOptions.setFreeCompilerArgs() {
    val optIns = mutableListOf(
        "kotlin.contracts.ExperimentalContracts",
        "kotlin.ExperimentalUnsignedTypes",
        "kotlin.ExperimentalStdlibApi",
        "kotlin.experimental.ExperimentalTypeInference",
    )
    if (this is KotlinJvmCompilerOptions) {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        // `kotlin.io.path` ships only in the JVM standard library, so for common
        // and Native compilations this opt-in marker is unresolved and the compiler
        // warns about it. Scope it to JVM compilations; multiplatform common and
        // Native code cannot use the API anyway.
        optIns.add("kotlin.io.path.ExperimentalPathApi")
    }
    // `-Xcontext-parameters` is deliberately absent. Context parameters are
    // no longer experimental in the Kotlin version we pin, so at its default
    // language version (2.4) the flag only produces
    // "The argument '-Xcontext-parameters' is redundant for the current
    // language version 2.4."
    //
    // Re-add it if Kotlin is ever downgraded below 2.4, or if a compilation
    // this function configures pins a lower language version while using the
    // feature. (Precompiled script plugins compile against the Kotlin that
    // Gradle embeds and are not configured here.)
    //
    // Re-check these flags on a Kotlin bump: when the compiler reports one as
    // redundant, drop it.
    freeCompilerArgs.addAll(
        listOf(
            "-Xskip-prerelease-check",
            "-Xexpect-actual-classes",
            "-opt-in=" + optIns.joinToString(separator = ","),
        )
    )
}
