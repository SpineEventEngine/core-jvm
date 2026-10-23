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

@file:Suppress("unused")    /* Some constants may be used throughout the Spine repos. */

package io.spine.gradle

import java.io.File
import java.net.URI
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.SKIP_SUBTREE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.notExists
import org.ajoberstar.grgit.Grgit
import org.gradle.api.tasks.TaskContainer

/**
 * The name of the folder with the Gradle build logic of a project.
 */
private const val BUILD_SRC = "buildSrc"

/**
 * A tool to execute the Gradle `build` task in selected Git repositories
 * with the local version of [config] contents.
 *
 * Checks out the content of selected repositories into the specified [tempFolder].
 * The folder is created if it does not exist.
 *
 * Both [config] and [tempFolder] must be absolute paths, e.g. obtained from
 * `layout.projectDirectory`. A relative path is resolved against the working directory
 * of the Gradle daemon, which is unrelated to the project directory.
 *
 * Replaces the `config` and `buildSrc` folders in the checked-out repository by the local versions
 * of code. If the repository-under-test already contains its own `buildSrc` or `config` folders,
 * they are NOT overwritten, but rather renamed into `buildSrc-original` and `config-original`
 * accordingly. This allows further tracing if the build fails.
 *
 * Uses Gradle's [tasks] container to register itself as a Gradle task.
 *
 * This tool uses `println`s to print out its state. This is done to simplify the configuration
 * and dependencies.
 *
 * When running the Gradle build for each repository, a [RunBuild] task is used. Error and debug
 * logs of each Gradle test build are written according to this task's implementation.
 */
class ConfigTester(
    private val config: Path,
    private val tasks: TaskContainer,
    private val tempFolder: File
) {

    companion object {

        /**
         * Gradle build timeout.
         */
        private const val BUILD_TIMEOUT_MINUTES = 30L
    }

    init {
        require(config.isAbsolute) {
            "The path to `config` must be absolute. Encountered: `$config`."
        }
        require(tempFolder.isAbsolute) {
            "The path to the temp folder must be absolute. Encountered: `$tempFolder`."
        }
    }

    private val buildSrc: Path = config.resolve(BUILD_SRC)

    /**
     * Git repositories to test.
     */
    private val repos: MutableList<GitRepository> = ArrayList()

    /**
     * Adds a Git [repo] into the test build by its URI.
     *
     * The `master` branch is used as the one to checkout.
     */
    fun addRepo(repo: URI): ConfigTester {
        repos.add(GitRepository(repo))
        return this
    }

    /**
     * Adds a Git [repo] into the test build by its URI, to be checked out
     * at the given [branch].
     */
    fun addRepo(repo: URI, branch: Branch): ConfigTester {
        repos.add(GitRepository(repo, branch))
        return this
    }

    fun registerUnder(taskName: String) {
        val tasksPerRepo = repos.map { testWithConfig(it) }

        tasks.register(taskName) {
            group = SpineTaskGroup.name
            description = "Builds every configured downstream repository against this `config`"
            for (repoTaskName in tasksPerRepo) {
                dependsOn(repoTaskName)
            }
        }
    }

    private fun testWithConfig(gitRepo: GitRepository): String {
        val runGradleName = runGradleTask(gitRepo)
        doRegisterRunBuild(runGradleName, gitRepo)

        val executeBuildName = executeBuildTask(gitRepo)
        doRegisterExecuteBuild(executeBuildName, gitRepo, runGradleName)
        return executeBuildName
    }

    private fun doRegisterExecuteBuild(
        executeBuildName: String,
        gitRepo: GitRepository,
        runGradleName: String
    ) {
        tasks.register(executeBuildName) {
            group = SpineTaskGroup.name
            description = "Checks out `${gitRepo.name}` and overlays local `config` and `buildSrc`"
            doLast {
                println(" *** Testing `config` and `config/buildSrc` with `${gitRepo.name}`. ***")
                val ignoredFolder = tempFolder.toPath()
                gitRepo.checkout(tempFolder)
                    .replaceBuildSrc(buildSrc, ignoredFolder).replaceConfig(config, ignoredFolder)
            }
            finalizedBy(runGradleName)
        }
    }

    private fun doRegisterRunBuild(
        runGradleName: String,
        gitRepo: GitRepository,
    ) {
        tasks.register(runGradleName, RunBuild::class.java) {
            group = SpineTaskGroup.name
            description = "Runs the Gradle build of `${gitRepo.name}` against the local `config`"
            doFirst {
                println("`${gitRepo.name}`: starting Gradle build...")
            }
            doLast {
                println("*** `${gitRepo.name}`: Gradle build completed. ***")
            }
            directory = gitRepo.prepareCheckout(tempFolder).absolutePath
            maxDurationMins = BUILD_TIMEOUT_MINUTES
        }
    }

    private fun runGradleTask(repo: GitRepository): String {
        return "run-gradle-${repo.name}"
    }

    private fun executeBuildTask(repo: GitRepository): String {
        return "execute-build-${repo.name}"
    }
}

/**
 * A repository of source code hosted using Git.
 */
class GitRepository(

    /**
     * URI pointing to the location of the repository.
     */
    private val uri: URI,

    /**
     * A branch to check out.
     *
     * May be any branch of the repository, not only the default one.
     *
     * By default, points to `master`.
     */
    private val branch: Branch = Branch("master"),
) {
    /**
     * The name of this repository.
     */
    val name: String

    init {
        name = repoName(uri)
    }

    fun prepareCheckout(destinationFolder: File): File {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        val result = destinationFolder.toPath().resolve(name)
        Files.createDirectories(result)
        return result.toFile()
    }

    /**
     * Performs the checkout of the source code for this repository
     * to the specified [destinationFolder].
     *
     * The source code is put to the sub-folder named after the repository.
     * E.g., for `https://github.com/acme-org/foobar` the code is placed under
     * the `destinationFolder/foobar` folder.
     *
     * If the supplied folder does not exist, it is created.
     *
     * The [branch] is checked out as a local branch tracking its remote counterpart.
     * A [branch] the remote does not advertise fails the checkout.
     */
    fun checkout(destinationFolder: File): ClonedRepo {
        val preparedFolder = prepareCheckout(destinationFolder).toPath()
        println(
            "Checking out the `$uri` repository at `${branch.name}` " +
                    "to `${preparedFolder.toAbsolutePath()}`."
        )

        // The branch is checked out by the `clone` itself, rather than by a subsequent
        // `checkout`. A fresh clone has only the default branch among its local refs,
        // so `checkout` cannot resolve any other branch by its plain name, while
        // `refToCheckout` is resolved against the refs advertised by the remote,
        // where every branch of the repository is present.
        //
        // Only the checked-out files are of interest further on, so the Git repository
        // the clone opens is closed right away.
        Grgit.clone(
            mapOf(
                "dir" to preparedFolder,
                "uri" to uri,
                "refToCheckout" to branch.name
            )
        ).close()
        return ClonedRepo(this, preparedFolder)
    }

    private fun repoName(resourceLocation: URI): String {
        var path = resourceLocation.path
        if (path.endsWith('/')) {
            path = path.substring(0, path.length - 1)
        }
        val fromLastSlash = path.lastIndexOf('/') + 1
        val repoName = path.substring(fromLastSlash)
        return repoName
    }

    /**
     * Returns a new Git repository pointing to some particular Git [branch].
     */
    fun at(branch: Branch): GitRepository {
        return GitRepository(uri, branch)
    }
}

/**
 * The cloned Git repository.
 */
class ClonedRepo(

    /**
     * Origin Git repository that is cloned.
     */
    private val repo: GitRepository,

    /**
     * The location into which the [repo] is cloned.
     */
    private val location: Path
) {

    /**
     * Replaces the `buildSrc` folder in this cloned repository by the contents
     * of the folder defined by the [source].
     *
     * [source] is expected to be another `buildSrc` folder.
     *
     * The original `buildSrc` folder, if it exists in this cloned repo, is renamed
     * to `buildSrc-original`.
     *
     * Optionally, takes an [ignoredFolder] that will be excluded from the [source] paths
     * when copying.
     *
     * Build output and Git metadata of the [source] are not copied. If the original
     * `buildSrc` has its own `module.gradle.kts`, the script is preserved.
     *
     * Returns this instance of `ClonedRepo`, for call chaining.
     */
    fun replaceBuildSrc(source: Path, ignoredFolder: Path?): ClonedRepo {
        replaceFolder(BUILD_SRC, source, ignoredFolder)?.let(::restoreModuleScript)
        return this
    }

    /**
     * Restores the `module.gradle.kts` script of this repository, if it has one.
     *
     * `config` ships this script as an empty template that each project replaces
     * by its own. The `config/migrate` script preserves the script of a project
     * when installing `buildSrc` into it, and so must the test build.
     *
     * @param originalBuildSrc
     *         the folder into which the original `buildSrc` of this repository was renamed.
     */
    private fun restoreModuleScript(originalBuildSrc: Path) {
        val ownScript = originalBuildSrc.resolve(MODULE_SCRIPT_PATH)
        if (!ownScript.exists()) {
            return
        }
        println("Preserving the `$MODULE_SCRIPT_PATH` of `${repo.name}`.")
        val destination = location.resolve(BUILD_SRC).resolve(MODULE_SCRIPT_PATH)
        destination.parent.createDirectories()
        ownScript.copyTo(destination, overwrite = true)
    }

    /**
     * Replaces the `config` folder in this cloned repository by the contents
     * of the folder defined by the [source].
     *
     * [source] is expected to be another `config` folder.
     *
     * The original `config` folder, if it exists in this cloned repo, is renamed
     * to `config-original`.
     *
     * Optionally, takes an [ignoredFolder] that will be excluded from the [source] paths
     * when copying.
     *
     * Build output and Git metadata of the [source] are not copied.
     *
     * Returns this instance of `ClonedRepo`, for call chaining.
     */
    fun replaceConfig(source: Path, ignoredFolder: Path?): ClonedRepo {
        replaceFolder("config", source, ignoredFolder)
        return this
    }

    /**
     * Replaces the [folderName] folder of this repository by the contents of the [source].
     *
     * The original folder, if any, is renamed into `<folderName>-original`.
     *
     * Returns the path to which the original folder was renamed, or `null`
     * if this repository has no such folder.
     */
    private fun replaceFolder(folderName: String, source: Path, ignoredFolder: Path?): Path? {
        val folder = location.resolve(folderName)
        val rawFolder = folder.toFile()
        var renamedInto: Path? = null
        if (rawFolder.exists() && rawFolder.isDirectory) {
            val toRenameInto = location.resolve("$folderName-original")
            println("Renaming ${folder.toAbsolutePath()} into ${toRenameInto.toAbsolutePath()}.")
            Files.move(folder, toRenameInto)
            renamedInto = toRenameInto
        }
        println(
            "Copying the files from ${source.toAbsolutePath()} " +
                    "into ${folder.toAbsolutePath()}."
        )
        copyFolder(source, ignoredFolder, folder)
        return renamedInto
    }

    @Suppress("TooGenericExceptionCaught")
    private fun copyFolder(sourceFolder: Path, ignoredFolder: Path?, destinationFolder: Path) {
        try {
            copyRecursively(sourceFolder, ignoredFolder, destinationFolder)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Error copying folder `$sourceFolder` to `$destinationFolder`.", e
            )
        }
    }

    /**
     * Copies the [sourceFolder] into the [destinationFolder], skipping the [ignoredFolder]
     * along with the build output and the Git metadata.
     *
     * A symbolic link to a folder is copied as an empty folder, as the tree is walked
     * without following the links. A link that does not resolve is skipped: `config`
     * has such links when its `.agents/shared` submodule is not checked out.
     */
    private fun copyRecursively(sourceFolder: Path, ignoredFolder: Path?, destinationFolder: Path) {
        val ignored = ignoredFolder?.toAbsolutePath()

        fun Path.isIgnored(): Boolean =
            ignored?.let { toAbsolutePath().startsWith(it) } ?: false

        fun Path.destination(): Path = destinationFolder.resolve(sourceFolder.relativize(this))

        // The tree is walked rather than streamed, so that an entry which is not copied
        // is not descended into either. During the test build the `tmp` folder alone holds
        // the full clones of all the tested repositories.
        Files.walkFileTree(sourceFolder, object : SimpleFileVisitor<Path>() {

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != sourceFolder && (dir.isIgnored() || dir.isNonSource())) {
                    return SKIP_SUBTREE
                }
                dir.destination().createDirectories()
                return CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val danglingLink = attrs.isSymbolicLink && file.notExists()
                if (!danglingLink && !file.isIgnored() && !file.isNonSource()) {
                    file.copyTo(file.destination())
                }
                return CONTINUE
            }
        })
    }

    /**
     * Tells if this entry holds the output of a build or the Git metadata,
     * and hence must not be copied.
     *
     * The build output of the local `config` is unrelated to the repository under test.
     * Git metadata additionally holds the IPC sockets of the Git file system monitor;
     * these cannot be copied at all.
     *
     * A `build` folder is recognized only next to a Gradle build script, so that
     * the source packages named `build` are copied as usual.
     */
    private fun Path.isNonSource(): Boolean {
        if (name in NON_SOURCE_ENTRIES) {
            return true
        }
        return name == BUILD_OUTPUT && parent?.holdsGradleBuild() == true
    }

    private fun Path.holdsGradleBuild(): Boolean = BUILD_SCRIPTS.any { resolve(it).exists() }

    private companion object {

        /**
         * Names of the entries that never hold sources, at any depth of a project.
         *
         * @see ClonedRepo.isNonSource
         */
        val NON_SOURCE_ENTRIES = setOf(".git", ".gradle", ".kotlin")

        /**
         * The name of the folder that holds the output of a Gradle build.
         *
         * @see ClonedRepo.isNonSource
         */
        const val BUILD_OUTPUT = "build"

        /**
         * The names of the scripts by which a folder is recognized as a Gradle build.
         */
        val BUILD_SCRIPTS = setOf("build.gradle.kts", "settings.gradle.kts")

        /**
         * The path, relative to `buildSrc`, to the script that each project
         * defines for itself.
         *
         * @see ClonedRepo.restoreModuleScript
         */
        const val MODULE_SCRIPT_PATH = "src/main/kotlin/module.gradle.kts"
    }
}

/**
 * Spine repositories at GitHub.
 *
 * The list is expected to grow over time.
 */
object SpineRepos {

    const val libsOrg: String = "https://github.com/SpineEventEngine/"
    const val examplesOrg: String = "https://github.com/spine-examples/"

    val base: URI = library("base")
    val baseTypes: URI = library("base-types")
    val coreJvm: URI = library("core-jvm")
    val web: URI = library("web")

    private fun library(repo: String) = URI(libsOrg + repo)
    private fun example(repo: String) = URI(examplesOrg + repo)
}

/**
 * A name of a Git branch.
 *
 * The [name] is a plain branch name, such as `master` or `grpc-concurrency-fixes`.
 * Neither the `refs/heads/` prefix nor the name of a remote is expected.
 */
data class Branch(val name: String)
