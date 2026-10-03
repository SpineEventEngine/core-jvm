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

package io.spine.gradle.github.pages

import io.spine.gradle.git.Repository
import java.io.File
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

/**
 * Performs the update of GitHub pages.
 */
fun Task.updateGhPages(project: Project) {
    val plugin = project.plugins.getPlugin(UpdateGitHubPages::class.java)

    SshKey(plugin.rootFolder, logger).register()

    val repository = Repository.forPublishingDocumentation(project)

    val updateJavadocFormat =
        UpdateJavadocFormat(project, plugin.javadocOutputFolder, repository, logger)

    val updateHtmlFormat =
        UpdateHtmlFormat(project, plugin.htmlOutputFolder, repository, logger)

    repository.use {
        updateJavadocFormat.run()
        updateHtmlFormat.run()
        repository.push()
    }
}

private abstract class UpdateDocumentation(
    private val project: Project,
    private val docsSourceFolder: Path,
    private val repository: Repository,
    private val logger: Logger
) {

    /**
     * The folder under the repository's root(`/`) for storing documentation.
     *
     * The value should not contain any leading or trailing file separators.
     *
     * The absolute path to the project's documentation is made by appending its
     * name to the end, making `/docsDestinationFolder/project.name`.
     */
    protected abstract val docsDestinationFolder: String

    /**
     * The name of the format of the documentation to update.
     *
     * This name will appear in logs as part of a message.
     */
    protected abstract val formatName: String

    private val mostRecentFolder by lazy {
        File("${repository.location}/${docsDestinationFolder}/${project.name}")
    }

    private fun log(message: () -> String) {
        if (logger.isDebugEnabled) {
            logger.debug(message())
        }
    }

    fun run() {
        val module = project.name
        log { "Update of the `$formatName` documentation for the module `$module` started." }

        val documentation = replaceMostRecentDocs()
        copyIntoVersionDir(documentation)

        val version = project.version
        val updateMessage =
            "Update `$formatName` documentation for the module" +
                    " `$module` with the version `$version`."
        repository.commitAllChanges(updateMessage)

        log { "Update of the `$formatName` documentation for `$module` successfully finished." }
    }

    private fun replaceMostRecentDocs(): ConfigurableFileCollection {
        val generatedDocs = project.files(docsSourceFolder)

        log { "Replacing the most recent `$formatName` documentation in `$mostRecentFolder`." }
        copyDocs(generatedDocs, mostRecentFolder)

        return generatedDocs
    }

    private fun copyDocs(source: FileCollection, destination: File) {
        destination.mkdir()
        project.copy {
            from(source)
            into(destination)
        }
    }

    private fun copyIntoVersionDir(generatedDocs: ConfigurableFileCollection) {
        val versionedDocDir = File("$mostRecentFolder/v/${project.version}")

        log { "Storing the new version of `$formatName` documentation in `${versionedDocDir}`." }
        copyDocs(generatedDocs, versionedDocDir)
    }
}

private class UpdateJavadocFormat(
    project: Project,
    docsSourceFolder: Path,
    repository: Repository,
    logger: Logger
) : UpdateDocumentation(project, docsSourceFolder, repository, logger) {

    override val docsDestinationFolder: String
        get() = "javadoc"
    override val formatName: String
        get() = "javadoc"
}

private class UpdateHtmlFormat(
    project: Project,
    docsSourceFolder: Path,
    repository: Repository,
    logger: Logger
) : UpdateDocumentation(project, docsSourceFolder, repository, logger) {

    override val docsDestinationFolder: String
        get() = "reference"
    override val formatName: String
        get() = "html"
}
