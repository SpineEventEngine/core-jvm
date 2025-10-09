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

package io.spine.gradle.publish

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.spine.gradle.repo.Repository
import java.io.FileNotFoundException
import java.net.URI
import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * A task which verifies that the current version of the library has not been published to the given
 * Maven repository yet.
 */
open class CheckVersionIncrement : DefaultTask() {

    /**
     * The Maven repository in which to look for published artifacts.
     *
     * We check both the `releases` and `snapshots` repositories. Artifacts in either of these repos
     * may not be overwritten.
     */
    @Input
    lateinit var repository: Repository

    @Input
    val version: String = project.version as String

    @TaskAction
    fun fetchAndCheck() {
        val artifact = "${project.artifactPath()}/${MavenMetadata.FILE_NAME}"
        val snapshots = repository.target(snapshots = true)
        checkInRepo(snapshots, artifact)

        if (!repository.hasOneTarget()) {
            checkInRepo(repository.target(snapshots = false), artifact)
        }
    }

    private fun checkInRepo(repoUrl: String, artifact: String) {
        val metadata = fetch(repoUrl, artifact)
        val versions = metadata?.versioning?.versions
        val versionExists = versions?.contains(version) ?: false
        if (versionExists) {
            throw GradleException(
                    """
                    The version `$version` is already published to the Maven repository `$repoUrl`.
                    Try incrementing the library version.
                    All available versions are: ${versions?.joinToString(separator = ", ")}.

                    To disable this check, run Gradle with `-x $name`.
                    """.trimIndent()
            )
        }
    }

    private fun fetch(repository: String, artifact: String): MavenMetadata? {
        val url = URI.create("$repository/$artifact").toURL()
        return MavenMetadata.fetchAndParse(url)
    }

    private fun Project.artifactPath(): String {
        val group = this.group as String
        val name = "${artifactPrefix()}${this.name}"

        val pathElements = ArrayList(group.split('.'))
        pathElements.add(name)
        val path = pathElements.joinToString(separator = "/")
        return path
    }

    /**
     * Returns the artifact prefix used for the publishing of this project.
     *
     * All current Spine modules should be using `SpinePublishing`.
     * Therefore, the corresponding extension should be present in the root project.
     * However, just in case, we define the "standard" prefix here as well.
     *
     * This value MUST be the same as defined by the defaults in `SpinePublishing`.
     */
    private fun Project.artifactPrefix(): String {
        val ext = rootProject.extensions.findByType(SpinePublishing::class.java)
        val result = ext?.artifactPrefix ?: SpinePublishing.DEFAULT_PREFIX
        return result
    }
}

private data class MavenMetadata(var versioning: Versioning = Versioning()) {

    companion object {

        const val FILE_NAME = "maven-metadata.xml"

        private val mapper = XmlMapper()

        init {
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        /**
         * Fetches the metadata for the repository and parses the document.
         *
         * <p>If the document could not be found, assumes that the module was never
         * released and thus has no metadata.
         */
        fun fetchAndParse(url: URL): MavenMetadata? {
            return try {
                val metadata = mapper.readValue(url, MavenMetadata::class.java)
                metadata
            } catch (_: FileNotFoundException) {
                null
            }
        }
    }
}

private data class Versioning(var versions: List<String> = listOf())
