/*
 * Copyright 2026, TeamDev. All rights reserved.
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
    freeCompilerArgs.addAll(
        listOf(
            "-Xskip-prerelease-check",
            "-Xexpect-actual-classes",
            "-Xcontext-parameters",
            "-opt-in=" + optIns.joinToString(separator = ","),
        )
    )
}
