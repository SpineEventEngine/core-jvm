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

package io.spine.gradle.dart

import io.spine.gradle.dart.plugin.DartPlugins
import io.spine.gradle.dart.task.DartTasks
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/**
 * Configures [DartExtension] that facilitates configuration of Gradle tasks and plugins
 * to build Dart projects.
 *
 * The whole structure of the extension looks as follows:
 *
 * ```
 * dart {
 *     environment {
 *         // ...
 *     }
 *     plugins {
 *         // ...
 *     }
 *     tasks {
 *         // ...
 *     }
 * }
 * ```
 *
 * ### Environment
 *
 * One of the main features of this extension is [DartEnvironment]. Environment describes a module
 * itself and used tools with their input/output files.
 *
 * The extension is shipped with a pre-configured environment. So, no pre-configuration is required.
 * Most properties in [DartEnvironment] have calculated defaults right in the interface.
 * Only two properties need explicit override.
 *
 * The extension defines them as follows:
 *
 *  1. [DartEnvironment.projectDir] –> `project.projectDir`.
 *  2. [DartEnvironment.projectName] —> `project.name`.
 *
 * There are two ways to modify the environment:
 *
 *  1. Modify [DartEnvironment] interface directly. Go with this option when it is a global change
 *     that should affect all projects which use this extension.
 *  2. Use [DartExtension.environment] scope — for temporary and custom overridings.
 *
 * An example of a property overriding:
 *
 * ```
 * dart {
 *     environment {
 *         integrationTestDir = projectDir.resolve("tests")
 *     }
 * }
 * ```
 *
 * Please note, environment should be set up firstly to have the effect on the parts
 * of the extension that use it.
 *
 * ### Tasks and Plugins
 *
 * The spirit of tasks configuration in this extension is extracting the code that defines and
 * registers tasks into extension functions upon `DartTasks` in `buildSrc`. Those extensions should
 * be named after a task it registers or a task group if several tasks are registered at once.
 * Then this extension is called in a project's `build.gradle.kts`.
 *
 * `DartTasks` and `DartPlugins` scopes extend [DartContext] which provides access
 * to the current [DartEnvironment] and shortcuts for running `pub` tool.
 *
 * Below is the simplest example of how to create a primitive `printPubVersion` task.
 *
 * Firstly, a corresponding extension function should be defined in `buildSrc`:
 *
 * ```
 * fun DartTasks.printPubVersion() =
 *     register<Exec>("printPubVersion") {
 *         pub("--version")
 *     }
 * ```
 *
 * Secondly, in a project's `build.gradle.kts` this extension is called:
 *
 * ```
 * dart {
 *     tasks {
 *         printPubVersion()
 *     }
 * }
 * ```
 *
 * An extension function is not restricted to register exactly one task. If several tasks can
 * be grouped into a logical bunch, they should be registered together:
 *
 * ```
 * fun DartTasks.build() {
 *     assembleDart()
 *     testDart()
 *     generateCoverageReport()
 * }
 *
 * private fun DartTasks.assembleDart() = ...
 *
 * private fun DartTasks.testDart() = ...
 *
 * private fun DartTasks.generateCoverageReport() = ...
 * ```
 *
 * This section is mostly dedicated to tasks. But tasks and plugins are configured
 * in a very similar way. So, everything above is also applicable to plugins. More detailed
 * guides can be found in docs to `DartTasks` and `DartPlugins`.
 *
 * @see [ConfigurableDartEnvironment]
 * @see [DartTasks]
 * @see [DartPlugins]
 */
fun Project.dart(configuration: DartExtension.() -> Unit) {
    extensions.run {
        configuration.invoke(
            findByType() ?: create("dartExtension", project)
        )
    }
}

/**
 * Scope for performing Dart-related configuration.
 *
 * @see [dart]
 */
open class DartExtension(project: Project) {

    private val environment = ConfigurableDartEnvironment(
        object : DartEnvironment {
            override val projectDir = project.projectDir
            override val projectName = project.name
        }
    )

    private val tasks = DartTasks(environment, project)
    private val plugins = DartPlugins(environment, project)

    /**
     * Overrides default values of [DartEnvironment].
     *
     * Please note, environment should be set up firstly to have the effect on the parts
     * of the extension that use it.
     */
    fun environment(overridings: ConfigurableDartEnvironment.() -> Unit) =
        environment.run(overridings)

    /**
     * Configures [Dart-related plugins][DartPlugins].
     */
    fun plugins(configurations: DartPlugins.() -> Unit) = plugins.run(configurations)

    /**
     * Configures [Dart-related tasks][DartTasks].
     */
    fun tasks(configurations: DartTasks.() -> Unit) = tasks.run(configurations)
}
