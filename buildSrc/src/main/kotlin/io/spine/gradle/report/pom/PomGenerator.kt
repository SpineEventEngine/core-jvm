/*
 * Copyright 2026, TeamDev. All rights reserved.
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
