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

package io.spine.gradle.dart

import org.gradle.api.Project
import org.gradle.api.tasks.Exec

/**
 * Provides access to the current [DartEnvironment] and shortcuts for running the `pub` tool.
 */
open class DartContext(dartEnv: DartEnvironment, internal val project: Project)
    : DartEnvironment by dartEnv
{
    /**
     * Executes the `pub` command in this [Exec] task.
     *
     * The Dart ecosystem uses packages to manage shared software such as libraries and tools.
     * To get or publish Dart packages, the `pub` package manager is to be used.
     */
    fun Exec.pub(vararg args: Any) = commandLine(pubExecutable, *args)
}
