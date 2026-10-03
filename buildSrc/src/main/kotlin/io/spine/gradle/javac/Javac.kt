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
