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

import com.fasterxml.jackson.databind.JsonNode
import java.io.Serial
import java.io.Serializable
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

/**
 * Identifies a component — a project of this build, or a module — alike as Gradle
 * resolves it and as the SPDX Gradle Plugin lists it in an SBOM.
 *
 * @see ArtifactComponents
 */
@JvmInline
internal value class ComponentKey(private val value: String) : Serializable {

    override fun toString(): String = value

    companion object {

        @Serial
        private const val serialVersionUID: Long = 0L

        /**
         * Returns the key of the project with the given [path].
         */
        fun ofProject(path: String): ComponentKey = ComponentKey("project:$path")

        /**
         * Returns the key of the module with the given coordinates.
         */
        fun ofModule(group: String, name: String, version: String): ComponentKey =
            ComponentKey("module:$group:$name:$version")

        /**
         * Returns the key of the component with the given [id], or `null` if
         * the component is neither a project nor a module.
         */
        fun of(id: ComponentIdentifier): ComponentKey? = when (id) {
            is ProjectComponentIdentifier -> ofProject(id.projectPath)
            is ModuleComponentIdentifier ->
                ofModule(group = id.group, name = id.module, version = id.version)
            else -> null
        }

        /**
         * Returns the key of the component the given SPDX package stands for, or `null`
         * if the package tells neither the project nor the module it stands for.
         *
         * The SPDX Gradle Plugin tells the path of a project in the source information
         * of its package, and names the package of a module as `group:name`, followed by
         * the classifier of the file it describes, if any.
         */
        fun ofPackage(pkg: JsonNode): ComponentKey? =
            pkg[SpdxField.sourceInfo]?.asText()?.let(::projectPathIn)?.let(::ofProject)
                ?: moduleOf(pkg)

        /**
         * Returns the key of the module the given SPDX package stands for, or `null` if
         * the name of the package does not start with `group:name`, or the package has
         * no version.
         */
        private fun moduleOf(pkg: JsonNode): ComponentKey? {
            val coordinates = pkg[SpdxField.name]?.asText()?.split(':').orEmpty()
            val version = pkg[SpdxField.versionInfo]?.asText()
            return if (coordinates.size < 2 || version == null) {
                null
            } else {
                ofModule(group = coordinates[0], name = coordinates[1], version = version)
            }
        }
    }
}
