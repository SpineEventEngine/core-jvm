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
        group = SpineTaskGroup.name

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
 * The task installs an unpublished artifact of the `spine-web` library as a module dependency.
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
        group = SpineTaskGroup.name

        dependsOn(":client-js:publishJsLocally")

        doLast {
            npm("run", "installLinkedLib")
        }
    }
