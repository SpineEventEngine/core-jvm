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

package io.spine.gradle

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.api.errors.GitAPIException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("`GitRepository` should")
internal class GitRepositorySpec {

    @TempDir
    lateinit var tempDir: Path

    /** The repository to be cloned, playing the role of a remote. */
    private lateinit var origin: Path

    /** The folder the repository is checked out into. */
    private lateinit var destination: File

    /** The root of the checked-out repository. */
    private val clone: Path
        get() = destination.toPath().resolve(REPO_NAME)

    /**
     * Creates the repository to be cloned, with two branches.
     *
     * Both branches carry [SHARED_FILE]. Only [FEATURE] carries [FEATURE_FILE],
     * which thus tells the branches apart in the clone.
     *
     * The created repository is left at [MASTER], so that this is the branch
     * a clone checks out by default.
     */
    @BeforeEach
    fun createOrigin() {
        origin = tempDir.resolve(REPO_NAME).createDirectories()
        destination = tempDir.resolve("checkout").toFile()
        Grgit.init(mapOf("dir" to origin)).use { git ->
            origin.createFile(SHARED_FILE, SHARED_TEXT)
            git.commitAll("Add the file shared by all branches.")

            // Git does not mandate the name of the branch a repository starts with,
            // and JGit honors `init.defaultBranch`, so make sure `master` exists.
            if (git.branch.current().name != MASTER) {
                git.checkout(mapOf("branch" to MASTER, "createBranch" to true))
            }

            git.checkout(mapOf("branch" to FEATURE, "createBranch" to true))
            origin.createFile(FEATURE_FILE, FEATURE_TEXT)
            git.commitAll("Add the file of the feature branch.")

            git.checkout(mapOf("branch" to MASTER))
        }
    }

    @Test
    fun `check out the default branch`() {
        GitRepository(origin.toUri()).checkout(destination)

        clone.resolve(SHARED_FILE).readText() shouldBe SHARED_TEXT
        clone.resolve(FEATURE_FILE).shouldNotExist()
        headOfClone() shouldBe "$MASTER -> origin/$MASTER"
    }

    /**
     * Verifies that a branch is resolved by its plain name and is checked out
     * as a local branch rather than as a detached `HEAD`.
     *
     * A fresh clone has only the default branch among its local refs, so the branch
     * has to be resolved against the refs advertised by the remote. `HEAD` detached
     * at `origin/<branch>` would hold the right files just as well, which is why
     * the `HEAD` of the clone is asserted too.
     */
    @Test
    fun `check out a branch other than the default one`() {
        GitRepository(origin.toUri(), Branch(FEATURE)).checkout(destination)

        clone.resolve(FEATURE_FILE).readText() shouldBe FEATURE_TEXT
        headOfClone() shouldBe "$FEATURE -> origin/$FEATURE"
    }

    /**
     * Verifies that a missing branch fails the checkout right away.
     *
     * Were the clone to succeed and leave an empty working tree behind, the Gradle
     * build of the repository would fail later on with a far less obvious message.
     */
    @Test
    fun `fail to check out a branch that does not exist`() {
        val repo = GitRepository(origin.toUri(), Branch(MISSING))

        val error = shouldThrow<GitAPIException> { repo.checkout(destination) }

        // `GitAPIException` roots the whole JGit error hierarchy, so the branch
        // has to be named for this to pin the branch resolution rather than,
        // say, an unreadable `origin`.
        error.message shouldContain MISSING
    }

    /**
     * Returns the current branch of the clone, along with the branch it tracks,
     * as `<branch> -> <upstream>`.
     *
     * A detached `HEAD` reads as `HEAD -> null`.
     */
    private fun headOfClone(): String =
        Grgit.open(mapOf("dir" to clone)).use {
            val head = it.branch.current()
            "${head.name} -> ${head.trackingBranch?.name}"
        }

    private companion object {

        /** The name of the folder the repository is cloned into. */
        const val REPO_NAME = "acme-lib"

        const val MASTER = "master"
        const val FEATURE = "grpc-concurrency-fixes"
        const val MISSING = "no-such-branch"

        const val SHARED_FILE = "README.md"
        const val SHARED_TEXT = "The file shared by all branches."

        const val FEATURE_FILE = "src/main/kotlin/Concurrency.kt"
        const val FEATURE_TEXT = "object Concurrency"

        /**
         * Commits all the files of this repository, including the new ones.
         */
        fun Grgit.commitAll(message: String) {
            add(mapOf("patterns" to setOf(".")))
            commit(mapOf("message" to message))
        }
    }
}
