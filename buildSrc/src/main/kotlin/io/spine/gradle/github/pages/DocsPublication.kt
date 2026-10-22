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

import io.spine.gradle.VersionComparator
import io.spine.gradle.git.Repository
import java.io.File
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger

/**
 * Publishes the API documentation of the given [project] to GitHub Pages.
 *
 * The documentation in each format is copied into a clone of the `gh-pages` branch
 * and committed there, and the commits are pushed.
 *
 * The clone has only the most recent documentation of the [project] and the folder of
 * its version checked out, without the folders of the versions published earlier.
 * The branch holds the documentation of every version of every module, which is too
 * much to check out for a change to a single module.
 */
internal fun Task.publishToGitHubPages(project: Project) {
    val plugin = project.plugins.getPlugin(PublishApiDocs::class.java)

    SshKey(plugin.rootFolder, logger).register()

    val repository = Repository.forPublishingDocumentation(project)

    val javadoc = JavadocPublication(project, plugin.javadocOutputFolder, repository, logger)
    val html = HtmlPublication(project, plugin.htmlOutputFolder, repository, logger)

    repository.use {
        repository.checkOutOnly(javadoc.checkoutPatterns() + html.checkoutPatterns())
        javadoc.publish()
        html.publish()
        repository.push()
    }
}

/**
 * Publishes the documentation of a [project] in one format to the clone of
 * the [repository] serving GitHub Pages.
 *
 * The documentation goes to the folder of its version, and also replaces the most
 * recent documentation of the project, unless a newer version is already published.
 */
private abstract class DocsPublication(
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
     * The name of the format of the documentation to publish.
     *
     * This name will appear in logs as part of a message.
     */
    protected abstract val formatName: String

    /**
     * The path of the folder with the documentation of the [project] in this format,
     * relative to the repository root.
     */
    private val folder: String
        get() = "$docsDestinationFolder/${project.name}"

    private val mostRecentFolder by lazy {
        File("${repository.location}/$folder")
    }

    /**
     * The folder holding the documentation of each published version in a subfolder
     * named after the version.
     */
    private val versionsFolder by lazy {
        File(mostRecentFolder, VERSIONS_FOLDER)
    }

    private fun log(message: () -> String) {
        if (logger.isDebugEnabled) {
            logger.debug(message())
        }
    }

    /**
     * Obtains the patterns of the paths this publication writes, for
     * [checking out][Repository.checkOutOnly] just those paths.
     */
    fun checkoutPatterns(): List<String> =
        docsCheckoutPatterns(folder, project.version.toString())

    /**
     * Copies the documentation into the repository and commits it.
     */
    fun publish() {
        val module = project.name
        log { "Publishing the `$formatName` documentation of the module `$module`." }

        val documentation = replaceMostRecentDocs()
        copyIntoVersionDir(documentation)

        val version = project.version
        val commitMessage =
            "Publish the `$formatName` documentation of the module" +
                    " `$module` for the version `$version`."
        repository.commitAllChanges(commitMessage)

        log { "Published the `$formatName` documentation of the module `$module`." }
    }

    /**
     * Replaces the most recent documentation with the one of this version, unless
     * the documentation of a newer version is already published.
     *
     * The runs of `publish-api-docs.yml` are serialized, but not necessarily in the
     * order of their commits, and a release of an older line may come after a newer
     * one. Either way, the most recent documentation stays that of the newest version.
     */
    private fun replaceMostRecentDocs(): ConfigurableFileCollection {
        val generatedDocs = project.files(docsSourceFolder)

        val version = project.version.toString()
        val newer = publishedVersions().newestAbove(version)
        if (newer != null) {
            logger.lifecycle(
                "The most recent `$formatName` documentation of `${project.name}` stays" +
                        " that of `$newer`, which is newer than `$version`."
            )
            return generatedDocs
        }

        log { "Replacing the most recent `$formatName` documentation in `$mostRecentFolder`." }
        copyDocs(generatedDocs, mostRecentFolder)

        return generatedDocs
    }

    /**
     * Obtains the versions whose documentation is published, as named by the folders
     * under `v/`.
     *
     * The names are read from the tree of the current commit, as the folders of
     * the other versions are not checked out.
     */
    private fun publishedVersions(): List<String> =
        repository.listFolders("$folder/$VERSIONS_FOLDER")

    private fun copyDocs(source: FileCollection, destination: File) {
        destination.mkdirs()
        project.copy {
            from(source)
            into(destination)
        }
    }

    private fun copyIntoVersionDir(generatedDocs: ConfigurableFileCollection) {
        val versionedDocDir = File(versionsFolder, "${project.version}")

        log { "Storing the new version of `$formatName` documentation in `${versionedDocDir}`." }
        copyDocs(generatedDocs, versionedDocDir)
    }
}

private class JavadocPublication(
    project: Project,
    docsSourceFolder: Path,
    repository: Repository,
    logger: Logger
) : DocsPublication(project, docsSourceFolder, repository, logger) {

    override val docsDestinationFolder: String
        get() = "javadoc"
    override val formatName: String
        get() = "javadoc"
}

private class HtmlPublication(
    project: Project,
    docsSourceFolder: Path,
    repository: Repository,
    logger: Logger
) : DocsPublication(project, docsSourceFolder, repository, logger) {

    override val docsDestinationFolder: String
        get() = "reference"
    override val formatName: String
        get() = "html"
}

/**
 * Returns the newest of these versions if it is newer than the given [version],
 * or `null` otherwise.
 *
 * The versions are compared by [VersionComparator], so that `2.10.0` is newer than
 * `2.9.0`, and a release is newer than the snapshots leading to it.
 */
internal fun Iterable<String>.newestAbove(version: String): String? =
    maxWithOrNull(VersionComparator)?.takeIf { VersionComparator.compare(it, version) > 0 }

/**
 * The name of the folder that holds the documentation of each published version
 * in a subfolder named after the version.
 */
private const val VERSIONS_FOLDER = "v"

/**
 * Returns the patterns, in the `.gitignore` syntax, of the paths that a publication
 * of the documentation in the given [folder] writes: the most recent documentation,
 * and the folder of the given [version] under `v/`, but not the folders of the other
 * versions.
 *
 * The folders under `v/` are excluded one by one, and the folder of the [version]
 * is included again after them, as the last matching pattern wins. This is how
 * `git sparse-checkout` lays out its patterns in cone mode.
 *
 * For example, for the `javadoc/core` folder and the version `1.2.0`, the patterns
 * select everything in `javadoc/core`, exclude each folder in `javadoc/core/v`, and
 * select `javadoc/core/v/1.2.0` again.
 *
 * @param folder the path of the documentation folder, relative to the repository root.
 * @param version the version whose folder is included.
 */
internal fun docsCheckoutPatterns(folder: String, version: String): List<String> = listOf(
    "/$folder/*",
    "!/$folder/$VERSIONS_FOLDER/*/",
    "/$folder/$VERSIONS_FOLDER/$version/",
)
