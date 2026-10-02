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

package io.spine.gradle.javascript.task

import io.spine.gradle.SpineTaskGroup
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
        group = SpineTaskGroup.name

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
        group = SpineTaskGroup.name

        delete(
            genProtoMain,
            genProtoTest,
            nycOutput,
        )
    }
