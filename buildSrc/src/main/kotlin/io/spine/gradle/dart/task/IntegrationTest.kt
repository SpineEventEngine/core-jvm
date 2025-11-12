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

import io.spine.gradle.TaskName
import io.spine.gradle.named
import io.spine.gradle.register
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

private val integrationTestName = TaskName.of("integrationTest", Exec::class)

/**
 * Locates `integrationTest` task in this [TaskContainer].
 *
 * The task runs integration tests of the `spine-dart` library against a sample
 * Spine-based application. The tests are run in Chrome browser because they use `WebFirebaseClient`
 * which only works in web environment.
 *
 * A sample Spine-based application is run from the `test-app` module before integration
 * tests start and is stopped as the tests complete.
 */
val TaskContainer.integrationTest: TaskProvider<Exec>
    get() = named(integrationTestName)

/**
 * Registers [TaskContainer.integrationTest] task.
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
