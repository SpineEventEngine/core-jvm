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

import groovy.xml.MarkupBuilder
import java.io.StringWriter
import kotlin.reflect.KProperty
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * Information about the Gradle project.
 *
 * Includes group ID, artifact name, and the version.
 */
@Suppress("MemberVisibilityCanBePrivate") /* Property values accessed via `KProperty`. */
internal class ProjectMetadata
internal constructor(
    internal val project: Project,
    internal val groupId: String,
    internal val artifactId: String,
    internal val version: String
) {

    /**
     * Returns an XML string containing the project metadata.
     *
     * The XML format is compatible with the one defined for Maven's `pom.xml`.
     */
    override fun toString(): String {
        val writer = StringWriter()
        MarkupBuilder(writer).tagsFor(::groupId, ::artifactId, ::version)
        return writer.toString()
    }

    private fun MarkupBuilder.tagsFor(vararg property: KProperty<*>) {
        property.forEach {
            this.withGroovyBuilder {
                it.name { this@tagsFor.text(it.call()) }
            }
        }
    }
}

/**
 * Creates a new instance of [ProjectMetadata].
 *
 * The required information is first retrieved from the project.
 * And if a property is missing from the `project`, it is taken from the `extra` extension
 * of the project's root project.
 */
internal fun Project.metadata(): ProjectMetadata {
    val groupId = nonEmptyValue(group, "groupId")
    val artifactId = nonEmptyValue(name, "artifactId")
    val version = nonEmptyValue(this.version, "version")
    return ProjectMetadata(project, groupId, artifactId, version)
}

/**
 * Obtains the string form of the given [value].
 *
 * If that form is empty, falls back to the property named [key] in the `extra`
 * properties of the root project, failing if it is absent or not a `String`.
 */
private fun Project.nonEmptyValue(value: Any, key: String): String =
    value.toString().ifEmpty { rootProject.extra[key] as String }
