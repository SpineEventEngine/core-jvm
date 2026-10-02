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

package io.spine.gradle.publish

/**
 * A DSL element of [SpinePublishing] extension that allows enabling publishing
 * of [testJar] artifact.
 *
 * This artifact contains compilation output of `test` source set. By default, it is not published.
 *
 * Take a look on [SpinePublishing.testJar] for a usage example.

 * @see [artifacts]
 */
class TestJar {

    /**
     * Set of modules, for which a test JAR will be published.
     */
    var inclusions: Set<String> = emptySet()

    /**
     * Enables test JAR publishing for all published modules.
     */
    var enabled = false
}

/**
 * Flags for turning optional JAR artifacts in a project.
 *
 * @property sourcesJar Tells whether [sourcesJar] artifact should be published.
 *    Default value is `true`.
 * @property publishTestJar Tells whether [testJar] artifact should be published.
 */
internal data class JarFlags(
    val sourcesJar: Boolean = true,
    val publishTestJar: Boolean,
) {
    internal companion object {
        /**
         * Creates an instance of [JarFlags] for the project with the given name,
         * taking the setup parameters from JAR DSL elements.
         */
        fun create(projectName: String, testJar: TestJar): JarFlags {
            val addTestJar = testJar.inclusions.contains(projectName) || testJar.enabled
            return JarFlags(sourcesJar = true, addTestJar)
        }
    }
}
