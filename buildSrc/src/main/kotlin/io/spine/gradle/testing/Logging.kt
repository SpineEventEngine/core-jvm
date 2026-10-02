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

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/**
 * Configures logging of this [Test] task.
 *
 * Enables logging of:
 *  1. Standard `out` and `err` streams;
 *  2. Thrown exceptions.
 *
 *  Additionally, after all the tests are executed, a short summary would be logged. The summary
 *  consists of the number of tests and their results.
 *
 * Usage example:
 *
 *```
 * tasks {
 *     withType<Test> {
 *         configureLogging()
 *     }
 * }
 *```
 */
fun Test.configureLogging() {
    testLogging {
        showStandardStreams = true
        showExceptions = true
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }

    fun TestResult.summary(): String =
        """
        Test summary:
        >> $testCount tests
        >> $successfulTestCount succeeded
        >> $failedTestCount failed
        >> $skippedTestCount skipped
        """

    val listener = object : TestListener {

        override fun afterSuite(descriptor: TestDescriptor, result: TestResult) {
            // If the descriptor has no parent, then it is the root test suite,
            // i.e. it includes the info about all the run tests.
            if (descriptor.parent == null) {
                logger.lifecycle(result.summary())
            }
        }
    }

    addTestListener(listener)
}
