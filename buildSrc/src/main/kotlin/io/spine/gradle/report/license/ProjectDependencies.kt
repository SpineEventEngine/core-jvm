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

package io.spine.gradle.report.license

import com.github.jk1.license.ModuleData
import com.github.jk1.license.ProjectData
import io.spine.docs.MarkdownDocument

/**
 * Dependencies of some [Gradle project][ProjectData] classified by the Gradle configuration
 * (such as "runtime") to which they are bound.
 */
internal class ProjectDependencies
private constructor(
    private val runtime: Iterable<ModuleData>,
    private val compileTooling: Iterable<ModuleData>
) {

    internal companion object {

        /**
         * Creates an instance of [ProjectDependencies] by sorting the module dependencies.
         */
        fun of(data: ProjectData): ProjectDependencies {
            val runtimeDeps = mutableListOf<ModuleData>()
            val compileToolingDeps = mutableListOf<ModuleData>()
            data.configurations.forEach { config ->
                if (config.isOneOf(Configuration.runtime, Configuration.runtimeClasspath)) {
                    runtimeDeps.addAll(config.dependencies)
                } else {
                    compileToolingDeps.addAll(config.dependencies)
                }
            }
            return ProjectDependencies(runtimeDeps.toSortedSet(), compileToolingDeps.toSortedSet())
        }
    }

    /**
     * Prints the project dependencies along with the licensing information,
     * splitting them into "Runtime" and "Compile, tests, and tooling" sections.
     */
    internal fun printTo(out: MarkdownDocument) {
        out.printSection("Runtime", runtime)
            .printSection("Compile, tests, and tooling", compileTooling)
    }
}
