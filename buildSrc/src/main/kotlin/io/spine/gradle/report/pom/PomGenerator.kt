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

package io.spine.gradle.report.pom

import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.report.license.Paths
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

/**
 * Generates a `pom.xml` file that contains dependencies of the root project as
 * well as the dependencies of its subprojects.
 *
 * Usage:
 * ```
 *      PomGenerator.applyTo(project)
 * ```
 *
 * The generated `pom.xml` is not usable for Maven build tasks and is merely a
 * description of project dependencies.
 *
 * Configures the `build` task to generate the `pom.xml` file under `docs/dependencies`.
 *
 * Note that the generated `pom.xml` includes the group ID, artifact ID and the version of the
 * project this script was applied to. In case you want to override the default values, do so in
 * the `ext` block like so:
 *
 * ```
 * ext {
 *     groupId = 'custom-group-id'
 *     artifactId = 'custom-artifact-id'
 *     version = 'custom-version'
 * }
 * ```
 *
 * By default, those values are taken from the `project` object, which may or may not include
 * them. If the project does not have these values, and they are not specified in the `ext`
 * block, the resulting `pom.xml` file is going to contain empty blocks,
 * e.g., `<groupId></groupId>`.
 *
 * The version reported for each dependency is the one selected by dependency
 * resolution. A task of one project must not resolve the configurations of
 * another, so `generatePom` does not resolve anything itself. Instead, a helper
 * task named [ResolvedVersions.taskName] is registered for the project passed
 * to [applyTo] and each of its subprojects. Every helper resolves only the
 * configurations of its own project and stores the result under its build
 * directory; `generatePom` depends on the helpers and merges their outputs.
 * This keeps the generated file the same no matter which other tasks run in
 * the same Gradle invocation.
 */
@Suppress("unused")
object PomGenerator {

    private const val pomFilename = "pom.xml"

    /**
     * Configures the generator for the passed [project].
     */
    fun applyTo(project: Project) {

        /**
         * In some cases, the `base` plugin, which by default is added by e.g. `java`,
         * is not yet added.
         *
         * The `base` plugin defines the `build` task.
         * This generator needs it.
         */
        project.apply {
            plugin(BasePlugin::class.java)
        }

        val collectors = project.allprojects.map { ResolvedVersions.registerTaskIn(it) }

        val task = project.tasks.register("generatePom") {
            group = SpineTaskGroup.name
            description = "Generates a `pom.xml` file describing project dependencies"
            // Plain ordering on purpose: both the collectors and this task declare
            // no inputs or outputs, so they always run. Do not replace this with
            // input/output wiring — up-to-date skipping would reintroduce the
            // stale-report bug this design cures.
            dependsOn(collectors)
            doLast {
                val pomFile = Paths.outputFile(project.rootDir, pomFilename)
                pomFile.parentFile.mkdirs()

                val projectData = project.metadata()
                val writer = PomXmlWriter(projectData, ResolvedVersions::readFrom)
                writer.writeTo(pomFile)
            }

            val assembleTask = project.tasks.findByName("assemble")!!
            dependsOn(assembleTask)
        }

        val buildTask = project.tasks.findByName("build")!!
        buildTask.finalizedBy(task)
    }
}
