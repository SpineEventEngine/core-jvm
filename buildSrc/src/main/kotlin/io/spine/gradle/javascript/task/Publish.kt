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
import io.spine.gradle.named
import io.spine.gradle.publish.publish
import io.spine.gradle.register
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Registers tasks for publishing a JavaScript module.
 *
 * Please note, this task group depends on [assemble] tasks. Therefore, assembling tasks should
 * be applied in the first place.
 *
 * List of tasks to be created:
 *
 *  1. [TaskContainer.publishJs].
 *  2. [TaskContainer.publishJsLocally].
 *  3. [TaskContainer.prepareJsPublication].
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.task.assemble
 * import io.spine.gradle.javascript.task.publish
 *
 * // ...
 *
 * javascript {
 *     tasks {
 *         assemble()
 *         publish()
 *     }
 * }
 * ```
 */
fun JsTasks.publish() {

    transpileSources()
    prepareJsPublication()
    publishJsLocally()

    publishJs().also {
        publish.configure {
            dependsOn(it)
        }
    }
}

private val transpileSourcesName = TaskName.of("transpileSources")

/**
 * Locates `transpileSources` task in this [TaskContainer].
 *
 * The task transpiles JavaScript sources using Babel before their publishing.
 */
val TaskContainer.transpileSources: TaskProvider<Task>
    get() = named(transpileSourcesName)

private fun JsTasks.transpileSources() =
    register(transpileSourcesName) {

        description = "Transpiles JavaScript sources using Babel before their publishing."
        group = JsTasks.Group.publish

        doLast {
            npm("run", "transpile-before-publish")
        }
    }

private val prepareJsPublicationName = TaskName.of("prepareJsPublication")

/**
 * Locates `prepareJsPublication` task in this [TaskContainer].
 *
 * This is a lifecycle task that prepares the NPM package in
 * [publicationDirectory][io.spine.gradle.javascript.JsEnvironment.publicationDir]
 * of the current `JsEnvironment`.
 */
val TaskContainer.prepareJsPublication: TaskProvider<Task>
    get() = named(prepareJsPublicationName)

private fun JsTasks.prepareJsPublication() =
    register(prepareJsPublicationName) {

        description = "Prepares the NPM package for publishing."
        group = JsTasks.Group.publish

        // We need to copy two files into a destination directory without overwriting its content.
        // Default `Copy` task is not used since it overwrites the content of a destination
        // when copying there.
        // See issue: https://github.com/gradle/gradle/issues/1012

        doLast {
            project.copy {
                from(
                    packageJson,
                    npmrc
                )

                into(publicationDir)
            }
        }

        dependsOn(
            assembleJs,
            transpileSources
        )
    }

private val publishJsLocallyName = TaskName.of("publishJsLocally")

/**
 * Locates `publishJsLocally` task in this [TaskContainer].
 *
 * The task publishes the prepared NPM package locally using `npm link`.
 *
 *  @see <a href="https://docs.npmjs.com/cli/v8/commands/npm-link">npm-link | npm Docs</a>
 */
val TaskContainer.publishJsLocally: TaskProvider<Task>
    get() = named(publishJsLocallyName)

private fun JsTasks.publishJsLocally() =
    register(publishJsLocallyName) {

        description = "Publishes the NPM package locally with `npm link`."
        group = JsTasks.Group.publish

        doLast {
            publicationDir.npm("link")
        }

        dependsOn(prepareJsPublication)
    }

private val publishJsName = TaskName.of("publishJs")

/**
 * Locates `publishJs` task in this [TaskContainer].
 *
 * The task publishes the prepared NPM package from
 * [publicationDirectory][io.spine.gradle.javascript.JsEnvironment.publicationDir]
 * using `npm publish`.
 *
 * Please note, in order to publish an NMP package, a valid
 * [npmAuthToken][io.spine.gradle.javascript.JsEnvironment.npmAuthToken] should be
 * set. If no token is set, a default dummy value is quite enough for the local development.
 *
 * @see <a href="https://docs.npmjs.com/cli/v7/commands/npm-publish">npm-publish | npm Docs</a>
 */
val TaskContainer.publishJs: TaskProvider<Task>
    get() = named(publishJsName)

private fun JsTasks.publishJs() =
    register(publishJsName) {

        description = "Publishes the NPM package with `npm publish`."
        group = JsTasks.Group.publish

        doLast {
            publicationDir.npm("publish")
        }

        dependsOn(prepareJsPublication)
    }
