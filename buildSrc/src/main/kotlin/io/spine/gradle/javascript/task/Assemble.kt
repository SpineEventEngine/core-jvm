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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.gradle.GenerateProtoTask
import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.TaskName
import io.spine.gradle.base.assemble
import io.spine.gradle.javascript.plugin.generateJsonParsers
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.withType

/**
 * Registers tasks for assembling JavaScript artifacts.
 *
 * Please note, this task group depends on [mc-js][io.spine.gradle.javascript.plugin.mcJs]
 * and [protobuf][io.spine.gradle.javascript.plugin.protobuf]` plugins. Therefore,
 * these plugins should be applied in the first place.
 *
 * List of tasks to be created:
 *
 *  1. [TaskContainer.assembleJs].
 *  2. [TaskContainer.compileProtoToJs].
 *  3. [TaskContainer.installNodePackages].
 *  4. [TaskContainer.updatePackageVersion].
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.task.assemble
 *
 * // ...
 *
 * javascript {
 *     tasks {
 *         assemble()
 *     }
 * }
 * ```
 *
 * @param configuration any additional configuration related to the module's assembling.
 */
fun JsTasks.assemble(configuration: JsTasks.() -> Unit = {}) {

    installNodePackages()

    compileProtoToJs().also {
        generateJsonParsers.configure {
            dependsOn(it)
        }
    }

    updatePackageVersion()

    assembleJs().also {
        assemble.configure {
            dependsOn(it)
        }
    }

    configuration()
}

private val assembleJsName = TaskName.of("assembleJs")

/**
 * Locates `assembleJs` task in this [TaskContainer].
 *
 * It is a lifecycle task that produces consumable JavaScript artifacts.
 */
val TaskContainer.assembleJs: TaskProvider<Task>
    get() = named(assembleJsName)

private fun JsTasks.assembleJs() =
    register(assembleJsName) {

        description = "Assembles JavaScript sources into consumable artifacts."
        group = SpineTaskGroup.name

        dependsOn(
            installNodePackages,
            compileProtoToJs,
            updatePackageVersion,
            generateJsonParsers
        )
    }

private val compileProtoToJsName = TaskName.of("compileProtoToJs")

/**
 * Locates `compileProtoToJs` task in this [TaskContainer].
 *
 * The task is responsible for compiling Protobuf messages into JavaScript. It aggregates the tasks
 * provided by `protobuf` plugin that perform actual compilation.
 */
val TaskContainer.compileProtoToJs: TaskProvider<Task>
    get() = named(compileProtoToJsName)

private fun JsTasks.compileProtoToJs() =
    register(compileProtoToJsName) {

        description = "Compiles Protobuf messages into JavaScript."
        group = SpineTaskGroup.name

        withType<GenerateProtoTask>()
            .forEach { dependsOn(it) }
    }

private val installNodePackagesName = TaskName.of("installNodePackages")

/**
 * Locates `installNodePackages` task in this [TaskContainer].
 *
 * The task installs Node packages that this module depends on using `npm install` command.
 *
 * The `npm install` command is executed with the vulnerability check disabled since
 * it cannot fail the task execution despite on vulnerabilities found.
 *
 * To check installed Node packages for vulnerabilities execute
 * [TaskContainer.auditNodePackages] task.
 *
 * See [npm-install | npm Docs](https://docs.npmjs.com/cli/v8/commands/npm-install).
 */
val TaskContainer.installNodePackages: TaskProvider<Task>
    get() = named(installNodePackagesName)

private fun JsTasks.installNodePackages() =
    register(installNodePackagesName) {

        description = "Installs module`s Node dependencies."
        group = SpineTaskGroup.name

        inputs.file(packageJson)
        outputs.dir(nodeModules)

        doLast {
            npm("set", "audit", "false")
            npm("install")
        }
    }

private val updatePackageVersionName = TaskName.of("updatePackageVersion")

/**
 * Locates `updatePackageVersion` task in this [TaskContainer].
 *
 * The task sets the module's version in `package.json` to the value of
 * [moduleVersion][io.spine.gradle.javascript.JsEnvironment.moduleVersion]
 * specified in the current `JsEnvironment`.
 */
val TaskContainer.updatePackageVersion: TaskProvider<Task>
    get() = named(updatePackageVersionName)

private fun JsTasks.updatePackageVersion() =
    register(updatePackageVersionName) {

        description = "Sets a module's version in `package.json`."
        group = SpineTaskGroup.name

        doLast {
            val objectNode = ObjectMapper()
                .readValue(packageJson, ObjectNode::class.java)
                .put("version", moduleVersion)

            packageJson.writeText(

                // We are going to stick to JSON formatting used by `npm` itself.
                // So that modifying the line with the version would ONLY affect a single line
                // when comparing two files i.e. in Git.

                (objectNode.toPrettyString() + '\n')
                    .replace("\" : ", "\": ")
            )
        }
    }
