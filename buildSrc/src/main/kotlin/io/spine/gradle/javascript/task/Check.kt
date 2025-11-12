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
        group = JsTasks.Group.check

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
        group = JsTasks.Group.check

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
        group = JsTasks.Group.check

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
        group = JsTasks.Group.check

        doLast {
            npm("run", "test")
        }

        dependsOn(assembleJs)
        mustRunAfter(test)
    }
