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

package io.spine.gradle

import kotlin.reflect.KClass
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * A name and a type of a Gradle task.
 */
internal class TaskName<T : Task>(
    val value: String,
    val clazz: KClass<T>,
) {
    companion object {

        fun of(name: String) = TaskName(name, Task::class)

        fun <T : Task> of(name: String, clazz: KClass<T>) = TaskName(name, clazz)
    }
}

/**
 * Locates [the task][TaskName] in this [TaskContainer].
 */
internal fun <T : Task> TaskContainer.named(name: TaskName<T>) = named(name.value, name.clazz)

/**
 * Registers [the task][TaskName] in this [TaskContainer].
 */
internal fun <T : Task> TaskContainer.register(name: TaskName<T>, init: T.() -> Unit) =
    register(name.value, name.clazz, init)
