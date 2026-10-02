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

import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider

/**
 * Configures Error Prone for this `JavaCompile` task.
 *
 * Specifies the arguments for the compiler invocations. In particular, this configuration
 * overrides a number of Error Prone defaults. See [ErrorProneConfig] for the details.
 *
 * Please note that while `ErrorProne` is a standalone Gradle plugin,
 * it still has to be configured through `JavaCompile` task options.
 *
 * Here's an example of how to use it:
 *
 * ```
 * tasks {
 *     withType<JavaCompile> {
 *         configureErrorProne()
 *     }
 * }
 *```
 */
@Suppress("unused")
fun JavaCompile.configureErrorProne() {
    options.compilerArgs.add("--should-stop=ifError=FLOW")
    options.errorprone
        .errorproneArgumentProviders
        .add(ErrorProneConfig.ARGUMENTS)
}

/**
 * The knowledge that is required to set up `Error Prone`.
 */
private object ErrorProneConfig {

    /**
     * Command-line options for the `Error Prone` compiler.
     */
    val ARGUMENTS = CommandLineArgumentProvider {
        listOf(

            // Exclude generated sources from being analyzed by ErrorProne.
            // Include all directories started from `generated`, such as `generated-proto`.
            "-XepExcludedPaths:.*/generated.*/.*",

            // Turn the check off until ErrorProne can handle `@Nested` JUnit classes.
            // See issue: https://github.com/google/error-prone/issues/956
            "-Xep:ClassCanBeStatic:OFF",

            // Turn off checks that report unused methods and method parameters.
            // See issue: https://github.com/SpineEventEngine/config/issues/61
            "-Xep:UnusedMethod:OFF",
            "-Xep:UnusedVariable:OFF",

            "-Xep:CheckReturnValue:OFF",
            "-Xep:FloggerSplitLogStatement:OFF",
            "-Xep:FloggerLogString:OFF"
        )
    }
}
