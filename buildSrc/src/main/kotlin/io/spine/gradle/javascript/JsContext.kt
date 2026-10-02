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

package io.spine.gradle.javascript

import java.io.File
import org.gradle.api.Project
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations

/**
 * Provides access to the current [JsEnvironment] and shortcuts for running the `npm` tool.
 */
open class JsContext(jsEnv: JsEnvironment, internal val project: Project)
    : JsEnvironment by jsEnv
{
    /**
     * Executes the `npm` command in a separate process.
     *
     * [JsEnvironment.projectDir] is used as a working directory.
     */
    fun npm(vararg args: String) = projectDir.npm(*args)

    /**
     * Executes the `npm` command in a separate process.
     *
     * This [File] is used as a working directory.
     */
    fun File.npm(vararg args: String) = project.serviceOf<ExecOperations>().exec {
        workingDir(this@npm)
        commandLine(npmExecutable)
        args(*args)

        // Using private packages in a CI/CD workflow | npm Docs
        // https://docs.npmjs.com/using-private-packages-in-a-ci-cd-workflow

        environment["NPM_TOKEN"] = npmAuthToken
    }
}
