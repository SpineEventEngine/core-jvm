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

import io.spine.gradle.report.pom.PomFormatting.writeBlocks
import io.spine.gradle.report.pom.PomFormatting.writeStart
import java.io.File
import java.io.FileWriter
import java.io.StringWriter
import org.gradle.api.Project

/**
 * Writes the dependencies of a Gradle project and its subprojects as a `pom.xml` file.
 *
 * The resulting file is not usable for `maven` build tasks but serves as a description
 * of the first-level dependencies for each project or subproject.
 * Their transitive dependencies are not included in the result.
 *
 * The version of each dependency is taken from the map returned by
 * [resolvedVersionsOf] for the project the dependency comes from.
 * See the [dependencies] extension function for details.
 */
internal class PomXmlWriter
internal constructor(
    private val projectMetadata: ProjectMetadata,
    private val resolvedVersionsOf: (Project) -> Map<String, String>
) {

    /**
     * Writes the `pom.xml` file containing dependencies of this project
     * and its subprojects to the specified location.
     *
     * <p>If a file with the specified location exists, its contents will be substituted
     * with a new `pom.xml`.
     *
     * @param file a file to write `pom.xml` contents to.
     */
    fun writeTo(file: File) {
        val out = StringWriter()
        writeStart(out)
        writeBlocks(
            out,
            projectMetadata.toString(),
            InceptionYear.toString(),
            SpineLicense.toString(),
            projectDependencies()
        )
        PomFormatting.writeEnd(out)

        FileWriter(file).use {
            it.write(out.toString())
        }
    }

    /**
     * Obtains a string that contains project dependencies as XML.
     *
     * <p>The obtained string also contains a closing project tag.
     */
    private fun projectDependencies(): String {
        val destination = StringWriter()
        val dependencyWriter = DependencyWriter.of(projectMetadata.project, resolvedVersionsOf)
        dependencyWriter.writeXmlTo(destination)
        return destination.toString()
    }
}
