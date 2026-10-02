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
 *
 * Supposing, one needs to create a new task that would participate in building. Let the task name
 * be `testDart`. To do that, several steps should be completed:
 *
 *  1. Define the task name and type using [TaskName][io.spine.gradle.TaskName].
 *  2. Create a public typed reference for the task upon [TaskContainer]. It would facilitate
 *      referencing to the new task, so that external tasks could depend on it. This reference
 *      should be documented.
 *  3. Implement an extension upon [DartTasks] to register the task.
 *  4. Call the resulting extension from `build.gradle.kts`.
 *
 * Here's an example of `testDart()` extension:
 *
 * ```
 * import io.spine.gradle.SpineTaskGroup
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
 *         description = "Runs Dart tests declared in the `./test` directory"
 *         group = SpineTaskGroup.name
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
