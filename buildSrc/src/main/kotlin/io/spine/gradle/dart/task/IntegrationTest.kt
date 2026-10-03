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
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

private val integrationTestName = TaskName.of("integrationTest", Exec::class)

/**
 * Locates the `integrationTest` task in this [TaskContainer].
 *
 * The task runs integration tests of the `spine-dart` library against a sample
 * Spine-based application. The tests are run in the Chrome browser because they use `WebFirebaseClient`
 * that only works in a web environment.
 *
 * A sample Spine-based application is run from the `test-app` module before integration
 * tests start and is stopped as the tests complete.
 */
val TaskContainer.integrationTest: TaskProvider<Exec>
    get() = named(integrationTestName)

/**
 * Registers the [TaskContainer.integrationTest] task.
 *
 * Please note, this task depends on [build] tasks. Therefore, building tasks should be applied in
 * the first place.
 *
 * Here's an example of how to apply it in `build.gradle.kts`:
 *
 * ```
 * import io.spine.gradle.dart.dart
 * import io.spine.gradle.task.build
 * import io.spine.gradle.task.integrationTest
 *
 * // ...
 *
 * dart {
 *     tasks {
 *         build()
 *         integrationTest()
 *     }
 * }
 * ```
 */
@Suppress("unused")
fun DartTasks.integrationTest() =
    register(integrationTestName) {

        group = SpineTaskGroup.name
        description = "Runs integration tests of `spine-dart` against a sample application"

        dependsOn(
            resolveDependencies,
            ":test-app:appBeforeIntegrationTest"
        )

        pub(
            "run",
            "test",
            integrationTestDir,
            "-p",
            "chrome"
        )

        finalizedBy(":test-app:appAfterIntegrationTest")
    }
