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

package io.spine.gradle.base

import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named

/**
 * Locates `clean` task in this [TaskContainer].
 *
 * The task deletes the build directory and everything in it,
 * i.e. the path specified by the `Project.getBuildDir()` project property.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html#sec:base_tasks">
 *     Tasks | The Base Plugin</a>
 */
val TaskContainer.clean: TaskProvider<Delete>
    get() = named<Delete>("clean")

/**
 * Locates `check` task in this [TaskContainer].
 *
 * This is a lifecycle task that performs no action itself.
 *
 * Plugins and build authors should attach their verification tasks,
 * such as ones that run tests, to this lifecycle task using `check.dependsOn(myTask)`.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html#sec:base_tasks">
 *     Tasks | The Base Plugin</a>
 */
val TaskContainer.check: TaskProvider<Task>
    get() = named("check")

/**
 * Locates `assemble` task in this [TaskContainer].
 *
 * This is a lifecycle task that performs no action itself.
 *
 * Plugins and build authors should attach their assembling tasks that produce distributions and
 * other consumable artifacts to this lifecycle task using `assemble.dependsOn(myTask)`.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html#sec:base_tasks">
 *     Tasks | The Base Plugin</a>
 */
val TaskContainer.assemble: TaskProvider<Task>
    get() = named("assemble")

/**
 * Locates `build` task in this [TaskContainer].
 *
 * Intended to build everything, including running all tests, producing the production artifacts
 * and generating documentation. One will probably rarely attach concrete tasks directly
 * to `build` as [assemble][io.spine.gradle.base.assemble] and
 * [check][io.spine.gradle.base.check] are typically more appropriate.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html#sec:base_tasks">
 *     Tasks | The Base Plugin</a>
 */
val TaskContainer.build: TaskProvider<Task>
    get() = named("build")
