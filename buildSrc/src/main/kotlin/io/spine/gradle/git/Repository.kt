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

import com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly
import io.spine.gradle.Cli
import io.spine.gradle.fs.LazyTempPath
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.gradle.api.Project

/**
 * Interacts with a real Git repository.
 *
 * Clones the repository with the provided SSH URL in a temporary folder. Provides
 * functionality to configure a user, check out branches, commit changes and push them
 * to the remote repository.
 *
 * It is assumed that before using this class an appropriate SSH key that has
 * sufficient rights to perform described above operations was registered
 * in `ssh-agent`.
 *
 * NOTE: This class creates a temporary folder, so it holds resources. For the proper
 * release of resources please use the provided functionality inside a `use` block or
 * call the `close` method manually.
 *
 * @property project The Gradle project in which context the repo operations are held.
 * @property sshUrl The GitHub SSH URL to the underlying repository.
 * @property user Current user configuration.
 *   This configuration determines what ends up in the `author` and `committer` fields of a commit.
 * @property currentBranch The currently checked-out branch.
 */
@Suppress("TooManyFunctions") // A cohesive wrapper over many small `git` commands.
class Repository private constructor(
    private val project: Project,
    private val sshUrl: String,
    private var user: UserInfo,
    private var currentBranch: String,
) : AutoCloseable {

    /**
     * Path to the temporary folder for a clone of the underlying repository.
     */
    val location = LazyTempPath("repoTemp")

    /**
     * Clones the repository with [the SSH url][sshUrl] into the [temporary folder][location].
     */
    private fun clone() {
        repoExecute("git", "clone", sshUrl, ".")
    }

    /**
     * Executes a command in the [location].
     */
    private fun repoExecute(vararg command: String): String {
        val cmd = command.toList().joinToString(" ")
        val msg = "[Repo (${project.path})] Executing command: `$cmd`."
        System.err.println(msg)
        return Cli(location.toFile()).execute(*command)
    }

    /**
     * Checks out the branch by its name.
     *
     * IMPORTANT. The branch must exist in the upstream repository.
     * Use [checkoutOrCreate] to check out a branch that may not exist yet.
     */
    fun checkout(branch: String) {
        repoExecute("git", "checkout", branch)
        repoExecute("git", "pull")

        currentBranch = branch
    }

    /**
     * Checks out the [branch], creating it in the remote repository if it does
     * not exist yet.
     *
     * If the branch is already present on the remote, it is [checked out][checkout]
     * as usual. Otherwise, it is created as an orphan branch seeded with
     * [initialFiles] and pushed to the remote, so that subsequent commits with the
     * documentation have a branch to append to.
     *
     * Creating the branch on the fly makes the very first documentation publication
     * of a repository self-sufficient: the [documentation branch][Branch.documentation]
     * no longer needs to be created manually beforehand.
     *
     * @param branch the name of the branch to check out or create.
     * @param initialFiles the files — paths relative to the repository root mapped
     *   to their content — to add to the initial commit when the branch is created.
     *   Ignored when the branch already exists.
     */
    fun checkoutOrCreate(branch: String, initialFiles: Map<String, String> = emptyMap()) {
        if (remoteHasBranch(branch)) {
            // `remoteHasBranch` queries the remote directly via `git ls-remote`,
            // which does not populate `refs/remotes/origin/*`. In a parallel
            // build another module may have created the branch after this clone,
            // so fetch first to make the `origin/$branch` ref available;
            // otherwise `git checkout` cannot guess it and fails with a
            // pathspec error.
            repoExecute("git", "fetch", "origin")
            checkout(branch)
        } else {
            createOrphanBranch(branch, initialFiles)
        }
    }

    /**
     * Tells whether the remote repository has a branch with the given [name].
     *
     * Queries the fully qualified ref `refs/heads/$name` rather than the bare
     * [name]: `git ls-remote` treats a bare name as a tail glob and would also
     * match a namespaced branch such as `feature/$name`. Relies on `git ls-remote`
     * returning an empty output with a zero exit code when the branch is absent,
     * so the check does not raise an exception.
     */
    private fun remoteHasBranch(name: String): Boolean {
        val output = repoExecute("git", "ls-remote", "--heads", "origin", "refs/heads/$name")
        return output.isNotBlank()
    }

    /**
     * Creates the [branch] as an orphan branch seeded with [initialFiles] and
     * pushes it to the remote.
     *
     * `git switch --orphan` starts a new history with an empty working tree, so
     * the source code of the default branch does not leak into the created branch.
     * The [initialFiles] are written into this clean tree and staged before the
     * initial commit, which stays `--allow-empty` to support seeding no files.
     */
    private fun createOrphanBranch(branch: String, initialFiles: Map<String, String>) {
        repoExecute("git", "switch", "--orphan", branch)
        initialFiles.forEach { (path, content) ->
            location.toFile().resolve(path).writeText(content)
            repoExecute("git", "add", path)
        }
        repoExecute(
            "git",
            "commit",
            "--allow-empty",
            "--message=Initialize the `$branch` branch."
        )
        currentBranch = branch
        pushNewBranch(branch)
    }

    /**
     * Pushes the just-created [branch] to the remote, setting up the upstream tracking.
     *
     * If the push is rejected because a concurrently running publication created
     * the branch first (e.g., another module publishing documentation in the same
     * parallel build), the remote branch is [adopted][adoptRemoteBranch] instead.
     * Otherwise, the failure is genuine, and the original exception is rethrown.
     */
    private fun pushNewBranch(branch: String) {
        try {
            repoExecute("git", "push", "--set-upstream", "origin", branch)
        } catch (e: IllegalStateException) {
            // `Cli.execute` surfaces every non-zero `git` exit as an
            // `IllegalStateException`, so this branch handles a rejected push.
            // If the branch now exists on the remote, another module won the
            // creation race and we adopt its branch; otherwise the failure is
            // genuine and is rethrown.
            repoExecute("git", "fetch", "origin")
            if (!remoteHasBranch(branch)) {
                throw e
            }
            adoptRemoteBranch(branch)
        }
    }

    /**
     * Discards the local orphan branch in favour of the same-named branch that
     * already exists on the remote, keeping the local branch in sync with it.
     */
    private fun adoptRemoteBranch(branch: String) {
        repoExecute("git", "reset", "--hard", "origin/$branch")
        repoExecute("git", "branch", "--set-upstream-to=origin/$branch", branch)
    }

    /**
     * Configures the username and the email of the user.
     *
     * Overwrites `user.name` and `user.email` settings locally in [location] with
     * values from [user]. These settings determine what ends up in author and
     * committer fields of a commit.
     */
    fun configureUser(user: UserInfo) {
        repoExecute("git", "config", "user.name", user.name)
        repoExecute("git", "config", "user.email", user.email)

        this.user = user
    }

    /**
     * Stages all changes and commits with the provided message.
     */
    fun commitAllChanges(message: String) {
        stageAllChanges()
        commit(message)
    }

    private fun stageAllChanges() {
        repoExecute("git", "add", "--all")
    }

    private fun commit(message: String) {
        repoExecute(
            "git",
            "commit",
            "--allow-empty",
            "--message=${message}"
        )
    }

    /**
     * Pushes the current branch of the repository to the remote.
     *
     * Performs a pull with rebase before pushing to ensure the local branch is up-to-date.
     */
    fun push() {
        withRetries(description = "Pushing to $sshUrl, branch = '$currentBranch'") {
            repoExecute("git", "pull", "--rebase")
            repoExecute("git", "push")
        }
    }

    override fun close() {
        location.toFile().deleteRecursively()
    }

    companion object Factory {

        /**
         * Clones the repository with the provided SSH URL in a temporary folder.
         *
         * Configures the username and the email of the Git user.
         * See [configureUser] documentation for more information.
         *
         * Performs checkout of the branch in case it was passed.
         * By default, [master][Branch.master] is checked out. A non-default branch
         * that does not exist yet is created and seeded with [initialFiles].
         *
         * @throws IllegalArgumentException if SSH URL is an empty string.
         */
        fun clone(
            project: Project,
            sshUrl: String,
            user: UserInfo,
            branch: String = Branch.master,
            initialFiles: Map<String, String> = emptyMap(),
        ): Repository {
            require(sshUrl.isNotBlank()) { "SSH URL cannot be an empty string." }

            val repo = Repository(project, sshUrl, user, branch)
            repo.clone()
            repo.configureUser(user)

            if (branch != Branch.master) {
                repo.checkoutOrCreate(branch, initialFiles)
            }

            return repo
        }
    }
}

/**
 * Executes a given operation with retries using an exponential backoff strategy.
 *
 * If the operation fails, it will be retried up to the specified number of times
 * with increasing delays between attempts.
 * The delay increases exponentially but is capped at the specified maximum value.
 *
 * If all retries fail, the exception from the final attempt will be thrown to the caller.
 *
 * @param T the type of value returned by the operation
 * @param times the maximum number of attempts to execute the operation (default: 3)
 * @param initialDelay the delay before the first retry in milliseconds (default: 100ms)
 * @param maxDelay the maximum delay between retries in milliseconds (default: 2000ms)
 * @param factor the multiplier used to increase delay after each failure (default: 2.0)
 * @param description a description of the operation for error reporting (default: empty string)
 * @param block the operation to execute
 * @return the result of the successful operation execution
 */
@Suppress("TooGenericExceptionCaught", "LongParameterList")
private fun <T> withRetries(
    times: Int = 5,
    initialDelay: Long = 2000,      // ms
    maxDelay: Long = 20000,         // ms
    factor: Double = 2.0,
    description: String = "",
    block: () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            System.err.println("'$description' failed. " +
                    "Message: '${e.message}'. Retrying in $currentDelay ms.")
        }
        sleepUninterruptibly(currentDelay, MILLISECONDS)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block()
}
