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

package io.spine.gradle.testing

import io.spine.gradle.SpineTaskGroup
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Registers [slowTest][SlowTest] and [fastTest][FastTest] tasks in this [TaskContainer].
 *
 * Slow tests are registered to run after all fast tests.
 *
 * Usage example:
 *
 * ```
 * tasks {
 *     registerTestTasks()
 * }
 * ```
 */
@Suppress("unused")
fun TaskContainer.registerTestTasks() {
    withType<Test>().configureEach {
        filter {
            // There could be cases with no matching tests.
            // E.g., tests could be based on Kotest, which has custom task types and names.
            isFailOnNoMatchingTests = false
            includeTestsMatching("*Test")
            includeTestsMatching("*Spec")
        }
    }
    register<FastTest>("fastTest").let {
        register<SlowTest>("slowTest") {
            shouldRunAfter(it)
        }
    }
}

/**
 * Name of a tag for annotating a test class or method that is known to be slow and
 * should not normally be run together with the main test suite.
 *
 * @see <a href="https://spine.io/base/reference/testlib/io/spine/testing/SlowTest.html">
 *     SlowTest</a>
 * @see <a href="https://junit.org/junit5/docs/5.0.2/api/org/junit/jupiter/api/Tag.html">
 *     Tag</a>
 */
private const val SLOW_TAG = "slow"

/**
 * Executes JUnit tests filtering out the ones tagged as `slow`.
 */
private abstract class FastTest : Test() {
    init {
        description = "Executes all JUnit tests but the ones tagged as `slow`."
        group = SpineTaskGroup.name

        this.useJUnitPlatform {
            excludeTags(SLOW_TAG)
        }
    }
}

/**
 * Executes JUnit tests tagged as `slow`.
 */
private abstract class SlowTest : Test() {
    init {
        description = "Executes JUnit tests tagged as `slow`."
        group = SpineTaskGroup.name
        // No slow tests -- no problem.
        filter.isFailOnNoMatchingTests = false
        this.useJUnitPlatform {
            includeTags(SLOW_TAG)
        }
    }
}
