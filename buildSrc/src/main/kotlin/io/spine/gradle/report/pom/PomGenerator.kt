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

package io.spine.gradle.report.pom

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
 * Configures the `build` task to generate the `pom.xml` file.
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
 */
@Suppress("unused")
object PomGenerator {

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

        val task = project.tasks.register("generatePom") {
            doLast {
                val pomFile = project.projectDir.resolve("pom.xml")
                project.delete(pomFile)

                val projectData = project.metadata()
                val writer = PomXmlWriter(projectData)
                writer.writeTo(pomFile)
            }

            val assembleTask = project.tasks.findByName("assemble")!!
            dependsOn(assembleTask)
        }

        val buildTask = project.tasks.findByName("build")!!
        buildTask.finalizedBy(task)
    }
}
