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

import io.spine.gradle.publish.testJar
import org.gradle.api.Project

/**
 * Exposes the test classes of this project as a new "testArtifacts" configuration.
 *
 * This allows other projects to depend on the test classes from this project within a Gradle
 * multi-project build. It is helpful in case the dependant projects re-use abstract test suites
 * of some "parent" project.
 *
 * Please note that this utility requires Gradle `java` plugin to be applied. Hence, it is
 * recommended to call this extension method from `java` scope.
 *
 * Here's an example of how to expose the test classes of "projectA":
 *
 * ```
 * java {
 *     exposeTestConfiguration()
 * }
 * ```
 *
 * Here's an example of how to consume the exposed classes in "projectB":
 *
 * ```
 * dependencies {
 *     testImplementation(project(path = ":projectA", configuration = "testArtifacts"))
 * }
 * ```
 *
 * Don't forget that this exposure mechanism works only for projects that reside within the same
 * multi-project build. In order to share the test classes with external projects, publish a
 * dedicated [testJar][io.spine.gradle.publish.SpinePublishing.testJar] artifact.
 */
@Suppress("unused")
fun Project.exposeTestConfiguration() {

    check(pluginManager.hasPlugin("java")) {
        "Can't expose the test configuration because `java` plugin has not been applied."
    }

    configurations.create("testArtifacts") {
        extendsFrom(configurations.getByName("testRuntimeClasspath"))
        outgoing {
            artifact(testJar())
        }
    }
}
