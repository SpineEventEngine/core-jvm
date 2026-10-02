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

import LicenseSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the SBOM published with an artifact, naming the modules of this build by
 * the coordinates they are published under.
 *
 * The task rewrites the document that the SPDX Gradle Plugin writes for the dependencies
 * of the artifact — which the publications of a module share, unless one describes its
 * SBOM itself — into the SBOM of one [artifact][artifactCoordinates]:
 *
 *  - if the publication describes its SBOM, the artifact contains each package bundled
 *    into it, rather than depending on it, and the packages the artifact neither depends
 *    on nor bundles are left out, as [artifactComponents] tell;
 *  - the document is named after the artifact, and given a namespace derived from its
 *    coordinates, in place of the placeholder the plugin leaves there;
 *  - each package describing a module of this build — the artifact itself, and each
 *    sibling module it depends on — declares the [license][LicenseSettings.spdxId] of
 *    the modules of this build, is named `group:artifactId`, and gets the version and
 *    the package URL of the published artifact. The package URL replaces the external
 *    references the plugin wrote, if any. The plugin lists such a module under the name
 *    of its Gradle project, with neither a license nor a package URL.
 *
 * A module of this build is recognized by the source information the plugin gives it,
 * which ends with the path of the Gradle project in brackets; no other package has it.
 * A sibling without a single Maven publication to name it after — typically a module
 * bundled into the artifact of another one, such as a fat JAR — has no coordinates of
 * its own: it only gets the license, and keeps the name of its project.
 *
 * @see PublicationSbom
 */
@DisableCachingByDefault(because = "Rewrites a small document; caching it would not pay off.")
internal abstract class PublicationSbomTask : DefaultTask() {

    /**
     * The document written by the SPDX Gradle Plugin.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    /**
     * The path of the project whose artifact the SBOM describes.
     */
    @get:Input
    abstract val modulePath: Property<String>

    /**
     * The coordinates of the artifact the SBOM is published with, as
     * `group:artifactId:version`.
     */
    @get:Input
    abstract val artifactCoordinates: Property<String>

    /**
     * The platform the described artifact is built for, such as `jvm`, or
     * `native:macos_arm64` for a Kotlin/Native target.
     *
     * A multiplatform module publishes an artifact per target, and a dependency on it
     * resolves to the artifact of its target for the platform being built, however the
     * targets are named. So a module of this build that the described one depends on is
     * looked up by its publication for this platform first.
     */
    @get:Input
    abstract val platform: Property<String>

    /**
     * The coordinates of the Maven publications of this build that a module can depend on,
     * as `group:artifactId:version`.
     *
     * The publication of a Kotlin Multiplatform target is keyed by [publicationKey], with
     * the [platform] of the target. The only non-marker publication of a project is keyed
     * by the path of that project alone.
     */
    @get:Input
    abstract val publishedCoordinates: MapProperty<String, String>

    /**
     * The components the artifact depends on, and those bundled into it, if its
     * publication [describes][sbom] what its SBOM lists.
     *
     * Absent for any other publication, whose SBOM relates the artifact to the packages
     * as the plugin does: to each as a dependency.
     */
    @get:Optional
    @get:Input
    abstract val artifactComponents: Property<ArtifactComponents>

    /**
     * The SBOM to publish.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeSbom() {
        val mapper = ObjectMapper()
        val file = source.get().asFile
        val document = mapper.readTree(file) as? ObjectNode
            ?: error("`$file` is not an SPDX JSON document.")
        artifactComponents.orNull?.describeIn(document, logger)
        val published = publishedCoordinates.get()
        val own = Coordinates.parse(artifactCoordinates.get())
        document.put(SpdxField.name, own.artifactId)
        document.put(SpdxField.documentNamespace, own.namespace)
        document[SpdxField.packages]
            ?.filterIsInstance<ObjectNode>()
            ?.forEach { describeModule(pkg = it, own = own, published = published) }
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.get().asFile, document)
    }

    /**
     * Describes the given package as the module of this build it stands for, if it stands
     * for one: by the license of this build and, if the module has a single Maven
     * publication to name it after, by the coordinates of that publication.
     *
     * Any other package is left as the plugin wrote it.
     */
    private fun describeModule(
        pkg: ObjectNode,
        own: Coordinates,
        published: Map<String, String>
    ) {
        val path = pkg[SpdxField.sourceInfo]?.asText()?.let(::projectPathIn) ?: return
        pkg.put(SpdxField.licenseDeclared, LicenseSettings.spdxId)
        val module = if (path == modulePath.get()) own else published.lookUp(path, platform.get())
        if (module == null) {
            logger.info(
                "The SBOM of `${modulePath.get()}` lists the project `$path` under its" +
                        " project name: the project has no single Maven publication" +
                        " to name it after."
            )
            return
        }
        pkg.put(SpdxField.name, module.name)
        pkg.put(SpdxField.versionInfo, module.version)
        pkg.putArray(SpdxField.externalRefs).addObject().apply {
            put(SpdxField.referenceCategory, "PACKAGE-MANAGER")
            put(SpdxField.referenceLocator, module.purl)
            put(SpdxField.referenceType, "purl")
        }
    }
}

/**
 * Returns the key under which [PublicationSbomTask.publishedCoordinates] holds the
 * publication of the project with the given path for the given [platform].
 */
internal fun publicationKey(projectPath: String, platform: String): String =
    "$projectPath#$platform"

/**
 * Returns the coordinates of the project with the given [path], preferring its
 * publication for the given [platform], or `null` if the project has neither that
 * publication nor a single one.
 */
private fun Map<String, String>.lookUp(path: String, platform: String): Coordinates? =
    (get(publicationKey(path, platform)) ?: get(path))?.let(Coordinates::parse)

/**
 * Matches the path of a Gradle project at the end of the source information that the
 * SPDX Gradle Plugin writes for a module of this build: `git+<uri>@<revision>#<name>[<path>]`.
 */
private val projectPathSuffix = Regex("""\[(:[^\[\]]*)]$""")

/**
 * Returns the path of the Gradle project named in the given source information,
 * or `null` if the information does not name one.
 */
internal fun projectPathIn(sourceInfo: String): String? =
    projectPathSuffix.find(sourceInfo)?.groupValues?.get(1)

/**
 * The Maven coordinates of a published artifact.
 */
private data class Coordinates(val group: String, val artifactId: String, val version: String) {

    /** The name of the SPDX package describing the artifact. */
    val name: String get() = "$group:$artifactId"

    /** The package URL of the artifact. */
    val purl: String get() = "pkg:maven/$group/$artifactId@$version"

    /** The namespace of the SPDX document describing the artifact. */
    val namespace: String get() = "https://spine.io/spdxdocs/$group/$artifactId/$version"

    companion object {

        /** The number of parts in `group:artifactId:version`. */
        private const val partCount = 3

        /**
         * Parses coordinates written as `group:artifactId:version`.
         */
        fun parse(value: String): Coordinates {
            val parts = value.split(':')
            require(parts.size == partCount) {
                "Expected Maven coordinates as `group:artifactId:version`, got `$value`."
            }
            val (group, artifactId, version) = parts
            return Coordinates(group = group, artifactId = artifactId, version = version)
        }
    }
}
