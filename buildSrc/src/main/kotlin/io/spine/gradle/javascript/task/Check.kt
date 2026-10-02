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
import io.spine.gradle.base.check
import io.spine.gradle.java.test
import io.spine.gradle.javascript.isWindows
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Registers tasks for verifying a JavaScript module.
 *
 * Please note, this task group depends on [assemble] tasks. Therefore, assembling tasks should
 * be applied in the first place.
 *
 * List of tasks to be created:
 *
 *  1. [TaskContainer.checkJs].
 *  2. [TaskContainer.auditNodePackages].
 *  3. [TaskContainer.testJs].
 *  4. [TaskContainer.coverageJs].
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.task.assemble
 * import io.spine.gradle.javascript.task.check
 *
 * // ...
 *
 * javascript {
 *     tasks {
 *         assemble()
 *         check()
 *     }
 * }
 * ```
 *
 * @param configuration any additional configuration related to the module's verification.
 */
fun JsTasks.check(configuration: JsTasks.() -> Unit = {}) {

    auditNodePackages()
    coverageJs()
    testJs()

    checkJs().also {
        check.configure {
            dependsOn(it)
        }
    }

    configuration()
}

private val checkJsName = TaskName.of("checkJs")

/**
 * Locates `checkJs` task in this [TaskContainer].
 *
 * The task runs tests, audits NPM modules and creates a test-coverage report.
 */
val TaskContainer.checkJs: TaskProvider<Task>
    get() = named(checkJsName)

private fun JsTasks.checkJs() =
    register(checkJsName) {

        description = "Runs tests, audits NPM modules and creates a test-coverage report."
        group = SpineTaskGroup.name

        dependsOn(
            auditNodePackages,
            coverageJs,
            testJs,
        )
    }

private val auditNodePackagesName = TaskName.of("auditNodePackages")

/**
 * Locates `auditNodePackages` task in this [TaskContainer].
 *
 * The task audits the module dependencies using the `npm audit` command.
 *
 * The `audit` command submits a description of the dependencies configured in the module
 * to a public registry and asks for a report of known vulnerabilities. If any are found,
 * then the impact and appropriate remediation will be calculated.
 *
 * @see <a href="https://docs.npmjs.com/cli/v7/commands/npm-audit">npm-audit | npm Docs</a>
 */
val TaskContainer.auditNodePackages: TaskProvider<Task>
    get() = named(auditNodePackagesName)

private fun JsTasks.auditNodePackages() =
    register(auditNodePackagesName) {

        description = "Audits the module's Node dependencies."
        group = SpineTaskGroup.name

        inputs.dir(nodeModules)

        doLast {

            // `critical` level is set as the minimum level of vulnerability for `npm audit`
            // to exit with a non-zero code.

            npm("set", "audit-level", "critical")

            try {
                npm("audit")
            } catch (ignored: Exception) {
                npm("audit", "--registry", "https://registry.npmjs.eu")
            }
        }

        dependsOn(installNodePackages)
    }

private val coverageJsName = TaskName.of("coverageJs")

/**
 * Locates `coverageJs` task in this [TaskContainer].
 *
 * The task runs the JavaScript tests and collects the code coverage.
 */
val TaskContainer.coverageJs: TaskProvider<Task>
    get() = named(coverageJsName)

private fun JsTasks.coverageJs() =
    register(coverageJsName) {

        description = "Runs the JavaScript tests and collects the code coverage."
        group = SpineTaskGroup.name

        outputs.dir(nycOutput)

        doLast {
            npm("run", if (isWindows()) "coverage:win" else "coverage:unix")
        }

        dependsOn(assembleJs)
    }

private val testJsName = TaskName.of("testJs")

/**
 * Locates `testJs` task in this [TaskContainer].
 *
 * The task runs JavaScript tests.
 */
val TaskContainer.testJs: TaskProvider<Task>
    get() = named(testJsName)

private fun JsTasks.testJs() =
    register(testJsName) {

        description = "Runs JavaScript tests."
        group = SpineTaskGroup.name

        doLast {
            npm("run", "test")
        }

        dependsOn(assembleJs)
        mustRunAfter(test)
    }
