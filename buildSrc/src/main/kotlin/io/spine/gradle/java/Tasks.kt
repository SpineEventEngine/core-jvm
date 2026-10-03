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

package io.spine.gradle.java

import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named

/**
 * Locates the `test` task in this [TaskContainer].
 *
 * Runs the unit tests using JUnit or TestNG.
 *
 * Depends on `testClasses`, and all tasks that produce the test runtime classpath.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_tasks">
 *     Tasks | The Java Plugin</a>
 */
val TaskContainer.test: TaskProvider<Test>
    get() = named<Test>("test")
