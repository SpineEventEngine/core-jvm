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

package io.spine.gradle.dart.task

import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.TaskName
import io.spine.gradle.base.assemble
import io.spine.gradle.base.check
import io.spine.gradle.base.clean
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Registers tasks for building Dart projects.
 *
 * List of tasks to be created:
 *
 *  1. [TaskContainer.cleanPackageIndex].
 *  2. [TaskContainer.resolveDependencies].
 *  3. [TaskContainer.testDart].
 *
 * An example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.dart.dart
 * import io.spine.gradle.dart.task.build
 *
 * // ...
 *
 * dart {
 *     tasks {
 *         build()
 *     }
 * }
 * ```
 *
 * @param configuration any additional configuration related to the module's building.
 */
fun DartTasks.build(configuration: DartTasks.() -> Unit = {}) {

    cleanPackageIndex().also {
        clean.configure {
            dependsOn(it)
        }
    }
    resolveDependencies().also {
        assemble.configure {
            dependsOn(it)
        }
    }
    testDart().also {
        check.configure {
            dependsOn(it)
        }
    }

    configuration()
}

private val resolveDependenciesName = TaskName.of("resolveDependencies", Exec::class)

/**
 * Locates the `resolveDependencies` task in this [TaskContainer].
 *
 * The task fetches dependencies declared via `pubspec.yaml` using the `pub get` command.
 */
val TaskContainer.resolveDependencies: TaskProvider<Exec>
    get() = named(resolveDependenciesName)

private fun DartTasks.resolveDependencies(): TaskProvider<Exec> =
    register(resolveDependenciesName) {

        description = "Fetches dependencies declared via `pubspec.yaml`."
        group = SpineTaskGroup.name

        mustRunAfter(cleanPackageIndex)

        inputs.file(pubSpec)
        outputs.file(packageIndex)

        pub("get")
    }

private val cleanPackageIndexName = TaskName.of("cleanPackageIndex", Delete::class)

/**
 * Locates the `cleanPackageIndex` task in this [TaskContainer].
 *
 * The task deletes the resolved module dependencies' index.
 *
 * The standard configuration file that contains the index is `package_config.json`. For backwards
 * compatibility `pub` still updates the deprecated `.packages` file. The task deletes both files.
 */
val TaskContainer.cleanPackageIndex: TaskProvider<Delete>
    get() = named(cleanPackageIndexName)

private fun DartTasks.cleanPackageIndex(): TaskProvider<Delete> =
    register(cleanPackageIndexName) {

        description = "Deletes the resolved `.packages` and `package_config.json` files."
        group = SpineTaskGroup.name

        delete(
            packageIndex,
            packageConfig
        )
    }

private val testDartName = TaskName.of("testDart", Exec::class)

/**
 * Locates the `testDart` task in this [TaskContainer].
 *
 * The task runs Dart tests declared in the `./test` directory.
 */
val TaskContainer.testDart: TaskProvider<Exec>
    get() = named(testDartName)

private fun DartTasks.testDart(): TaskProvider<Exec> =
    register(testDartName) {

        description = "Runs Dart tests declared in the `./test` directory."
        group = SpineTaskGroup.name

        dependsOn(resolveDependencies)

        pub("run", "test")
    }
