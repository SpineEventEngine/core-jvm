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

package io.spine.gradle.javascript.task

import io.spine.gradle.TaskName
import io.spine.gradle.base.clean
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Registers tasks for deleting output of JavaScript builds.
 *
 * Please note, this task group depends on [assemble] tasks. Therefore, assembling tasks should
 * be applied in the first place.
 *
 * List of tasks to be created:
 *
 *  1. [TaskContainer.cleanJs].
 *  2. [TaskContainer.cleanGenerated].
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.task.assemble
 * import io.spine.gradle.javascript.task.clean
 *
 * // ...
 *
 * javascript {
 *     tasks {
 *         assemble()
 *         clean()
 *     }
 * }
 * ```
 */
fun JsTasks.clean() {

    cleanGenerated()

    cleanJs().also {
        clean.configure {
            dependsOn(it)
        }
    }
}

private val cleanJsName = TaskName.of("cleanJs", Delete::class)

/**
 * Locates `cleanJs` task in this [TaskContainer].
 *
 * The task deletes output of `assembleJs` task and output of its dependants.
 */
val TaskContainer.cleanJs: TaskProvider<Delete>
    get() = named(cleanJsName)

private fun JsTasks.cleanJs() =
    register(cleanJsName) {

        description = "Cleans output of `assembleJs` task and output of its dependants."
        group = JsTasks.Group.clean

        delete(
            assembleJs.map { it.outputs },
            compileProtoToJs.map { it.outputs },
            installNodePackages.map { it.outputs },
        )

        dependsOn(
            cleanGenerated
        )
    }

private val cleanGeneratedName = TaskName.of("cleanGenerated", Delete::class)

/**
 * Locates `cleanGenerated` task in this [TaskContainer].
 *
 * The task deletes directories with generated code and reports.
 */
val TaskContainer.cleanGenerated: TaskProvider<Delete>
    get() = named(cleanGeneratedName)

private fun JsTasks.cleanGenerated() =
    register(cleanGeneratedName) {

        description = "Cleans generated code and reports."
        group = JsTasks.Group.clean

        delete(
            genProtoMain,
            genProtoTest,
            nycOutput,
        )
    }
