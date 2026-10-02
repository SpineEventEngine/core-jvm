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

import io.spine.dependency.lib.Guava
import io.spine.dependency.local.TestLib
import io.spine.dependency.test.JUnit
import io.spine.dependency.test.JUnit.Jupiter
import io.spine.dependency.test.Kotest
import io.spine.dependency.test.Truth
import io.spine.gradle.testing.configureLogging
import io.spine.gradle.testing.registerTestTasks

/**
 * This convention plugin applies test dependencies and configures test-related tasks.
 *
 * The version of the [JUnit] platform must be applied via the [BomsPlugin][io.spine.dependency.boms.BomsPlugin]:
 *
 * ```kotlin
 * apply<BomsPlugin>()
 * ```
 */
@Suppress("unused")
private val about = ""

plugins {
    `java-library`
}

project.run {
    setupTests()
    forceTestDependencies()
}

dependencies {
    forceJunitPlatform()

    testImplementation(Jupiter.api)
    testImplementation(Jupiter.params)
    testImplementation(JUnit.pioneer)

    testImplementation(Guava.testLib)

    testImplementation(TestLib.lib)
    testImplementation(Kotest.assertions)

    testRuntimeOnly(Jupiter.engine)
}

/**
 * Forces the version of [JUnit] platform and its dependencies via [JUnit.bom].
 */
private fun DependencyHandlerScope.forceJunitPlatform() {
    testImplementation(enforcedPlatform(JUnit.bom))
}

typealias Module = Project

/**
 * Configures this module to run JUnit-based tests.
 */
fun Module.setupTests() {
    tasks {
        registerTestTasks()
        test.configure {
            useJUnitPlatform {
                includeEngines("junit-jupiter")
            }
            configureLogging()
        }
    }
}

/**
 * Forces the versions of task dependencies that are used _in addition_ to
 * the forced JUnit platform.
 */
@Suppress(
    /* We're OK with incubating API for configurations. It does not seem to change recently. */
    "UnstableApiUsage"
)
fun Module.forceTestDependencies() {
    configurations {
        all {
            resolutionStrategy {
                forceTestDependencies()
            }
        }
    }
}

private fun ResolutionStrategy.forceTestDependencies() {
    force(
        Guava.testLib,
        Truth.libs,
        Kotest.assertions,
    )
}
