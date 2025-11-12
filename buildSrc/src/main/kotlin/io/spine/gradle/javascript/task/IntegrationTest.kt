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
import io.spine.gradle.base.build
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

private val integrationTestName = TaskName.of("integrationTest")

/**
 * Locates `integrationTest` task in this [TaskContainer].
 *
 * The task runs integration tests of the `spine-web` library against
 * a sample Spine-based application.
 *
 * A sample Spine-based application is run from the `test-app` module before integration
 * tests and is stopped as the tests complete.
 *
 * See also: `./integration-tests/README.MD`
 */
val TaskContainer.integrationTest: TaskProvider<Task>
    get() = named(integrationTestName)

/**
 * Registers [TaskContainer.integrationTest] task.
 *
 * The task runs integration tests of the `spine-web` library against
 * a sample Spine-based application.
 *
 * Please note, this task depends on [assemble] and `client-js:publishJsLocally` tasks.
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.task.integrationTest
 *
 * // ...
 *
 * javascript {
 *     tasks {
 *         assemble()
 *         integrationTest()
 *     }
 * }
 * ```
 */
@Suppress("unused")
fun JsTasks.integrationTest() {

    linkSpineWebModule()

    register(integrationTestName) {

        // Find a way to run the same tests against `spine-web` in `client-js` module
        // to recover coverage.
        // See issue: https://github.com/SpineEventEngine/web/issues/96

        description = "Runs integration tests of the `spine-web` library " +
                "against the sample application."
        group = JsTasks.Group.check

        dependsOn(build, linkSpineWebModule, ":test-app:appBeforeIntegrationTest")

        doLast {
            npm("run", "test")
        }

        finalizedBy(":test-app:appAfterIntegrationTest")
    }
}

private val linkSpineWebModuleName = TaskName.of("linkSpineWebModule")

/**
 * Locates `linkSpineWebModule` task in this [TaskContainer].
 *
 * The task installs unpublished artifact of `spine-web` library as a module dependency.
 *
 * Creates a symbolic link from globally-installed `spine-web` library to `node_modules` of
 * the current project.
 *
 * See also: [npm-link | npm Docs](https://docs.npmjs.com/cli/v8/commands/npm-link)
 */
val TaskContainer.linkSpineWebModule: TaskProvider<Task>
    get() = named(linkSpineWebModuleName)

private fun JsTasks.linkSpineWebModule() =
    register(linkSpineWebModuleName) {

        description = "Install unpublished artifact of `spine-web` library as a module dependency."
        group = JsTasks.Group.assemble

        dependsOn(":client-js:publishJsLocally")

        doLast {
            npm("run", "installLinkedLib")
        }
    }
