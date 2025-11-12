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

@Suppress("unused", "ConstPropertyName")
@Deprecated(
    message = "Please use `KotlinX` from `io.spine.dependency.kotlinx` package",
    replaceWith = ReplaceWith(
        expression = "KotlinX",
        imports = ["io.spine.dependency.kotlinx.KotlinX"]
    )
)
object KotlinX {

    const val group = "org.jetbrains.kotlinx"

    @Deprecated(
        message = "Please use `Coroutines` from the `io.spine.dependency.kotlinx` package",
        replaceWith = ReplaceWith(
            expression = "Coroutines",
            imports = ["io.spine.dependency.kotlinx.Coroutines"]
        )
    )
    object Coroutines {

        // https://github.com/Kotlin/kotlinx.coroutines
        val version = io.spine.dependency.kotlinx.Coroutines.version
        val bom = "$group:kotlinx-coroutines-bom:$version"
        val core = "$group:kotlinx-coroutines-core:$version"
        val coreJvm = "$group:kotlinx-coroutines-core-jvm:$version"
        val jdk8 = "$group:kotlinx-coroutines-jdk8:$version"
        val debug = "$group:kotlinx-coroutines-debug:$version"
        val test = "$group:kotlinx-coroutines-test:$version"
        val testJvm = "$group:kotlinx-coroutines-test-jvm:$version"
    }
}
