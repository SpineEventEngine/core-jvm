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

package io.spine.gradle.dart.task

import io.spine.gradle.dart.DartContext
import io.spine.gradle.dart.DartEnvironment
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer

/**
 * A scope for registering and configuring Dart-related tasks.
 *
 * The scope provides:
 *
 *  1. Access to the current [DartContext].
 *  2. Project's [TaskContainer].
 *  3. Default task groups.
 *
 * Supposing, one needs to create a new task that would participate in building. Let the task name
 * be `testDart`. To do that, several steps should be completed:
 *
 *  1. Define the task name and type using [TaskName][io.spine.gradle.TaskName].
 *  2. Create a public typed reference for the task upon [TaskContainer]. It would facilitate
 *      referencing to the new task, so that external tasks could depend on it. This reference
 *      should be documented.
 *  3. Implement an extension upon [DartTasks] to register the task.
 *  4. Call the resulted extension from `build.gradle.kts`.
 *
 * Here's an example of `testDart()` extension:
 *
 * ```
 * import io.spine.gradle.named
 * import io.spine.gradle.register
 * import io.spine.gradle.TaskName
 * import org.gradle.api.Task
 * import org.gradle.api.tasks.TaskContainer
 * import org.gradle.api.tasks.Exec
 *
 * // ...
 *
 * private val testDartName = TaskName.of("testDart", Exec::class)
 *
 * /**
 *  * Locates `testDart` task in this [TaskContainer].
 *  *
 *  * The task runs Dart tests declared in the `./test` directory.
 *  */
 * val TaskContainer.testDart: TaskProvider<Exec>
 *     get() = named(testDartName)
 *
 * fun DartTasks.testDart() =
 *     register(testDartName) {
 *
 *         description = "Runs Dart tests declared in the `./test` directory."
 *         group = DartTasks.Group.build
 *
 *         // ...
 *     }
 * ```
 *
 * And here's how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.dart.dart
 * import io.spine.gradle.dart.task.testDart
 *
 * // ...
 *
 * dart {
 *     tasks {
 *         testDart()
 *     }
 * }
 * ```
 *
 * Declaring typed references upon [TaskContainer] is optional. But it is highly encouraged
 * to reference other tasks by such extensions instead of hard-typed string values.
 */
class DartTasks(dartEnv: DartEnvironment, project: Project)
    : DartContext(dartEnv, project), TaskContainer by project.tasks
{
    /**
     * Default task groups for tasks that participate in building a Dart module.
     *
     * @see [org.gradle.api.Task.getGroup]
     */
    internal object Group {
        const val build = "Dart/Build"
        const val publish = "Dart/Publish"
    }
}
