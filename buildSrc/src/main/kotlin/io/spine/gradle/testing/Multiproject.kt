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

import io.spine.gradle.publish.testJar
import org.gradle.api.Project

/**
 * Exposes the test classes of this project as a new "testArtifacts" configuration.
 *
 * This allows other projects to depend on the test classes from this project within a Gradle
 * multi-project build. It is helpful in case the dependent projects re-use abstract test suites
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
