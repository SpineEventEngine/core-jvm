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
