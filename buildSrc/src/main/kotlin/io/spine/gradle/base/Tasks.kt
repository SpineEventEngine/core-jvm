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
