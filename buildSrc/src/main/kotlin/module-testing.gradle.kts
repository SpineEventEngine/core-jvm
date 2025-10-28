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
 * Configure this module to run JUnit-based tests.
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
