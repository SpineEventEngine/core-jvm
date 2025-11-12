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

package io.spine.gradle.javascript

import java.io.File
import org.gradle.api.Project
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations

/**
 * Provides access to the current [JsEnvironment] and shortcuts for running `npm` tool.
 */
open class JsContext(jsEnv: JsEnvironment, internal val project: Project)
    : JsEnvironment by jsEnv
{
    /**
     * Executes `npm` command in a separate process.
     *
     * [JsEnvironment.projectDir] is used as a working directory.
     */
    fun npm(vararg args: String) = projectDir.npm(*args)

    /**
     * Executes `npm` command in a separate process.
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
