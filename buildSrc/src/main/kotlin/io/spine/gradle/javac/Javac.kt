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

package io.spine.gradle.javac

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider

/**
 * Configures the `javac` tool through this `JavaCompile` task.
 *
 * The following steps are performed:
 *
 *  1. Passes a couple of arguments to the compiler. See [JavacConfig] for more details;
 *  2. Sets the UTF-8 encoding to be used when reading Java source files.
 *
 * Here's an example of how to use it:
 *
 *```
 * tasks {
 *     withType<JavaCompile> {
 *         configureJavac()
 *     }
 * }
 *```
 */
@Suppress("unused")
fun JavaCompile.configureJavac() {
    with(options) {
        encoding = JavacConfig.SOURCE_FILES_ENCODING
        compilerArgumentProviders.add(JavacConfig.COMMAND_LINE)
    }
}

/**
 * The knowledge that is required to set up `javac`.
 */
private object JavacConfig {
    const val SOURCE_FILES_ENCODING = "UTF-8"
    val COMMAND_LINE = CommandLineArgumentProvider {
        listOf(

            // Protobuf Compiler generates the code, which uses the deprecated `PARSER` field.
            // See issue: https://github.com/SpineEventEngine/config/issues/173
            // "-Werror",

            "-Xlint:unchecked",
            "-Xlint:deprecation",
        )
    }
}
