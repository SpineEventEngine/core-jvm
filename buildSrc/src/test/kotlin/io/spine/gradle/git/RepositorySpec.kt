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

package io.spine.gradle.git

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.shouldBe
import io.spine.gradle.Cli
import io.spine.gradle.github.pages.docsCheckoutPatterns
import java.io.File
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Verifies a [sparse clone][Repository.Factory.cloneSparsely] against a local bare
 * repository, reached by a `file:` URL the way Git reaches a remote one: with a plain
 * path, Git would ignore `--depth` and `--filter`.
 *
 * The `gh-pages` branch of the repository holds a top-level `CNAME`, the documentation
 * of the module `m`, with the folders of its versions `1.0.0` and `1.1.0` and a file
 * next to them, and the documentation of the module `other`. The tests check out
 * the paths that [docsCheckoutPatterns] selects for `m`.
 */
@DisplayName("A sparse clone of a `Repository` should")
internal class RepositorySpec {

    @TempDir
    lateinit var tempDir: File

    private lateinit var remote: File

    /** A full clone of the [remote], standing in for the publications of other modules. */
    private lateinit var seed: File

    private lateinit var project: Project

    private val user = UserInfo("Docs Publisher", "docs@example.com")

    @BeforeEach
    fun createRemote() {
        remote = tempDir.resolve("remote.git")
        git(tempDir, "init", "--bare", "--initial-branch=master", remote.path)
        // Allow partial clones, with the blobs fetched on demand, as GitHub does.
        git(remote, "config", "uploadpack.allowFilter", "true")
        git(remote, "config", "uploadpack.allowAnySHA1InWant", "true")

        seed = tempDir.resolve("seed")
        git(tempDir, "clone", remote.path, seed.path)
        git(seed, "config", "user.name", "Seed")
        git(seed, "config", "user.email", "seed@example.com")
        seed.write("README.md", "The sources.")
        seed.commit("Add the sources.")
        git(seed, "push", "origin", "HEAD:refs/heads/master")

        git(seed, "switch", "--orphan", "gh-pages")
        seed.write("CNAME", "spine.io\n")
        seed.write("javadoc/m/index.html", "m, 1.1.0")
        seed.write("javadoc/m/v/1.0.0/index.html", "m, 1.0.0")
        seed.write("javadoc/m/v/1.1.0/index.html", "m, 1.1.0")
        seed.write("javadoc/m/v/notes.txt", "Not a version.")
        seed.write("javadoc/other/index.html", "other, 1.1.0")
        seed.commit("Publish the documentation.")
        git(seed, "push", "origin", "gh-pages")

        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.resolve("project"))
            .build()
    }

    @Test
    fun `check out only the top-level files`() {
        cloneDocs().use { repo ->
            val root = repo.location.toFile()

            root.resolve("CNAME").shouldExist()
            root.resolve("javadoc").shouldNotExist()
        }
    }

    @Test
    fun `check out only the paths of a publication`() {
        cloneDocs().use { repo ->
            repo.checkOutOnly(docsCheckoutPatterns("javadoc/m", "2.0.0"))
            val root = repo.location.toFile()

            root.resolve("javadoc/m/index.html").shouldExist()
            root.resolve("javadoc/m/v/1.0.0").shouldNotExist()
            root.resolve("javadoc/other").shouldNotExist()
        }
    }

    /**
     * Publishing a version again, as a re-run of a failed publication does, writes
     * into the folder of that version, which is in the branch already.
     */
    @Test
    fun `check out and update the folder of a version published before`() {
        cloneDocs().use { repo ->
            repo.checkOutOnly(docsCheckoutPatterns("javadoc/m", "1.1.0"))
            val root = repo.location.toFile()

            root.resolve("javadoc/m/v/1.1.0/index.html").shouldExist()
            root.resolve("javadoc/m/v/1.0.0").shouldNotExist()

            root.write("javadoc/m/v/1.1.0/index.html", "m, 1.1.0, again")
            repo.commitAllChanges("Publish `m` 1.1.0 again.")

            changedPaths(root) shouldContainExactly listOf("M\tjavadoc/m/v/1.1.0/index.html")
        }
    }

    @Test
    fun `list the folders that are not checked out, without the files`() {
        cloneDocs().use { repo ->
            repo.listFolders("javadoc/m/v") shouldContainExactly listOf("1.0.0", "1.1.0")
            repo.listFolders("javadoc/absent").shouldBeEmpty()
        }
    }

    @Test
    fun `commit only the changed paths, keeping those not checked out`() {
        cloneDocs().use { repo ->
            repo.checkOutOnly(docsCheckoutPatterns("javadoc/m", "2.0.0"))
            val root = repo.location.toFile()
            root.write("javadoc/m/index.html", "m, 2.0.0")
            root.write("javadoc/m/v/2.0.0/index.html", "m, 2.0.0")

            repo.commitAllChanges("Publish `m` 2.0.0.")

            changedPaths(root) shouldContainExactlyInAnyOrder listOf(
                "M\tjavadoc/m/index.html",
                "A\tjavadoc/m/v/2.0.0/index.html"
            )
        }
    }

    @Test
    fun `push onto a commit that another publication pushed meanwhile`() {
        cloneDocs().use { repo ->
            repo.checkOutOnly(docsCheckoutPatterns("javadoc/m", "2.0.0"))
            repo.location.toFile().write("javadoc/m/v/2.0.0/index.html", "m, 2.0.0")
            repo.commitAllChanges("Publish `m` 2.0.0.")

            seed.write("javadoc/other/index.html", "other, 2.0.0")
            seed.commit("Publish `other` 2.0.0.")
            git(seed, "push", "origin", "gh-pages")

            repo.push()
        }

        val log = git(remote, "log", "--format=%s", "gh-pages").lines()
        log shouldContain "Publish `m` 2.0.0."
        log shouldContain "Publish `other` 2.0.0."
        git(remote, "show", "gh-pages:javadoc/m/v/2.0.0/index.html") shouldBe "m, 2.0.0"
        git(remote, "show", "gh-pages:javadoc/other/index.html") shouldBe "other, 2.0.0"
        git(remote, "show", "gh-pages:javadoc/m/v/1.0.0/index.html") shouldBe "m, 1.0.0"
    }

    @Test
    fun `create a branch that does not exist yet, and push to it`() {
        val cname = "spine.io\n"

        cloneSite(initialFiles = mapOf("CNAME" to cname)).use { repo ->
            git(remote, "ls-tree", "--name-only", "site").trim() shouldBe "CNAME"
            git(remote, "show", "site:CNAME") shouldBe cname

            repo.checkOutOnly(docsCheckoutPatterns("javadoc/m", "1.0.0"))
            repo.location.toFile().write("javadoc/m/index.html", "m, 1.0.0")
            repo.commitAllChanges("Publish `m` 1.0.0.")
            repo.push()
        }

        git(remote, "show", "site:javadoc/m/index.html") shouldBe "m, 1.0.0"
    }

    @Test
    fun `adopt a branch that another publication created first, and push to it`() {
        val preparedCommit = raceForSiteBranch()

        cloneSite(initialFiles = mapOf("CNAME" to "spine.io\n")).use { repo ->
            val root = repo.location.toFile()
            git(root, "rev-parse", "HEAD").trim() shouldBe preparedCommit
            git(root, "rev-parse", "--abbrev-ref", "site@{upstream}").trim() shouldBe
                    "origin/site"

            raceHook.delete()
            repo.checkOutOnly(docsCheckoutPatterns("javadoc/m", "1.0.0"))
            root.write("javadoc/m/index.html", "m, 1.0.0")
            repo.commitAllChanges("Publish `m` 1.0.0.")
            repo.push()
        }

        git(remote, "show", "site:javadoc/m/index.html") shouldBe "m, 1.0.0"
    }

    /**
     * The full [clone][Repository.Factory.clone] shares the adoption of a branch with
     * the sparse one.
     */
    @Test
    fun `adopt a branch that another publication created first, in a full clone`() {
        val preparedCommit = raceForSiteBranch()

        Repository.clone(
            project,
            sshUrl = url,
            user = user,
            branch = "site",
            initialFiles = mapOf("CNAME" to "spine.io\n")
        ).use { repo ->
            val root = repo.location.toFile()
            git(root, "rev-parse", "HEAD").trim() shouldBe preparedCommit
            git(root, "rev-parse", "--abbrev-ref", "site@{upstream}").trim() shouldBe
                    "origin/site"
        }
    }

    /**
     * Makes another publication create the `site` branch after this one has found it
     * missing, and push first.
     *
     * The `pre-receive` hook of the [remote] stands in for that publication: it creates
     * the branch from a prepared commit, and rejects the push of this publication.
     *
     * @return the prepared commit.
     */
    private fun raceForSiteBranch(): String {
        git(seed, "switch", "--orphan", "prepared")
        seed.write("CNAME", "spine.io\n")
        seed.commit("Initialize the `site` branch.")
        git(seed, "push", "origin", "prepared:refs/heads/prepared-site")
        // Git keeps the objects a push brings in a quarantine until the hook accepts
        // them, and refuses to update a reference from there, so the hook leaves it.
        raceHook.apply {
            writeText(
                """
                #!/bin/sh
                env -u GIT_QUARANTINE_PATH -u GIT_OBJECT_DIRECTORY \
                    -u GIT_ALTERNATE_OBJECT_DIRECTORIES \
                    git update-ref refs/heads/site refs/heads/prepared-site
                exit 1
                """.trimIndent()
            )
            setExecutable(true)
        }
        return git(seed, "rev-parse", "HEAD").trim()
    }

    private val raceHook: File
        get() = remote.resolve("hooks/pre-receive")

    private val url: String
        get() = remote.toPath().toUri().toString()

    /**
     * Obtains the paths the last commit in the [root] of a clone changed, each
     * with the letter of its change.
     */
    private fun changedPaths(root: File): List<String> =
        git(root, "show", "--name-status", "--format=", "HEAD")
            .lines()
            .filter { it.isNotBlank() }

    private fun cloneDocs(): Repository =
        Repository.cloneSparsely(project, sshUrl = url, user = user, branch = "gh-pages")

    /**
     * Clones the `site` branch, which the [remote] does not have at first.
     */
    private fun cloneSite(initialFiles: Map<String, String>): Repository =
        Repository.cloneSparsely(
            project,
            sshUrl = url,
            user = user,
            branch = "site",
            initialFiles = initialFiles
        )
}

private fun git(directory: File, vararg arguments: String): String =
    Cli(directory).execute("git", *arguments)

private fun File.write(path: String, content: String) {
    resolve(path).apply {
        parentFile.mkdirs()
        writeText(content)
    }
}

private fun File.commit(message: String) {
    git(this, "add", "--all")
    git(this, "commit", "--message=$message")
}
