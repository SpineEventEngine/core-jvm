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
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Configures `assembleJs` task and creates `copyBundledJs` task to work with `webpack` bundler.
 *
 * Please note, this task group depends on [assemble] and [publish] tasks. Therefore, those tasks
 * should be applied in the first place.
 *
 * In particular, this method:
 *
 *  1. Extends `assembleJs` task to bundle sources during assembling.
 *  2. Creates `copyBundledJs` task and binds it to `prepareJsPublication` task execution.
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.task.assemble
 * import io.spine.gradle.javascript.task.publish
 * import io.spine.gradle.javascript.task.webpack
 *
 * // ...
 *
 * javascript {
 *     tasks {
 *         assemble()
 *         publish()
 *         webpack()
 *     }
 * }
 * ```
 */
@Suppress("unused")
fun JsTasks.webpack() {

    assembleJs.configure {

        outputs.dir(webpackOutput)

        doLast {
            npm("run", "build")
            npm("run", "build-dev")
        }
    }

    // Temporarily don't publish a bundle.
    // See: https://github.com/SpineEventEngine/web/issues/61

    copyBundledJs()/*.also {
        prepareJsPublication.configure {
            dependsOn(it)
        }
    }*/
}

private val copyBundledJsName = TaskName.of("copyBundledJs", Copy::class)

/**
 * Locates `copyBundledJs` task in this [TaskContainer].
 *
 * The task copies bundled JavaScript sources to the publication directory.
 */
@Suppress("unused")
val TaskContainer.copyBundledJs: TaskProvider<Copy>
    get() = named(copyBundledJsName)

private fun JsTasks.copyBundledJs() =
    register(copyBundledJsName) {

        description = "Copies bundled JavaScript sources to the NPM publication directory."
        group = SpineTaskGroup.name

        from(assembleJs.map { it.outputs })
        into(webpackPublicationDir)
    }
