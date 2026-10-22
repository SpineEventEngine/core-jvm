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
     * Clones the tip of the [branch], or of the default branch if it is `null`,
     * into the [temporary folder][location].
     *
     * The blobs are fetched only when the working tree needs them, and the working
     * tree holds only the top-level files until [checkOutOnly] replaces them with
     * the selected paths.
     */
    private fun cloneSparsely(branch: String?) {
        val options = listOf("--depth", "1", "--filter=blob:none", "--sparse", "--single-branch")
        val branchOption = branch?.let { listOf("--branch", it) } ?: emptyList()
        repoExecute(listOf("git", "clone") + options + branchOption + listOf("--", sshUrl, "."))
    }

    /**
     * Executes a command in the [location].
     */
    private fun repoExecute(vararg command: String): String = repoExecute(command.asList())

    /**
     * Executes a command, assembled as a list, in the [location].
     */
    private fun repoExecute(command: List<String>): String {
        val cmd = command.joinToString(" ")
        val msg = "[Repo (${project.path})] Executing command: `$cmd`."
        System.err.println(msg)
        return Cli(location.toFile()).execute(command)
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
     *
     * @param name the name of the branch.
     * @param remote the name of the remote or its URL; `origin` by default.
     */
    private fun remoteHasBranch(name: String, remote: String = "origin"): Boolean {
        val output = repoExecute("git", "ls-remote", "--heads", remote, "refs/heads/$name")
        return output.isNotBlank()
    }

    /**
     * Checks out only the paths of the current branch that match the given [patterns],
     * leaving the rest of the branch out of the working tree.
     *
     * The call replaces the paths checked out before, including the top-level files
     * of a [sparse clone][Factory.cloneSparsely], so it has to be given every path
     * to keep at once.
     *
     * The patterns follow the `.gitignore` syntax, so a folder can be checked out
     * without some of its subfolders. The paths left out stay in the branch: committing
     * [all changes][commitAllChanges] does not delete them.
     *
     * Leaving out some subfolders of a folder takes the non-cone mode of
     * `git sparse-checkout`, which Git documents as deprecated, though Git 2.54 still
     * supports it without a warning. The mode needs Git 2.35 or newer.
     */
    fun checkOutOnly(patterns: List<String>) {
        repoExecute(listOf("git", "sparse-checkout", "set", "--no-cone") + patterns)
    }

    /**
     * Obtains the names of the folders in the given [folder] of the current commit,
     * whether they are checked out or not.
     *
     * Reads the tree of the current commit, so the changes in the working tree that
     * are not committed yet are not reflected. The files in the [folder] are skipped.
     *
     * @param folder the path of the folder relative to the repository root.
     * @return the names of the folders in [folder], or an empty list if there is
     *   no such folder.
     */
    fun listFolders(folder: String): List<String> {
        require(folder.isNotBlank()) { "The folder to list cannot be blank." }
        // Each entry is `<mode> <type> <object>\t<path>`, the entries are separated
        // by `NUL`, and the paths are not quoted.
        return repoExecute("git", "ls-tree", "-z", "HEAD", "${folder.trimEnd('/')}/")
            .split('\u0000')
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val (info, path) = entry.split('\t', limit = 2)
                path.substringAfterLast('/').takeIf { info.split(' ')[1] == "tree" }
            }
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
            if (!remoteHasBranch(branch)) {
                throw e
            }
            adoptRemoteBranch(branch)
        }
    }

    /**
     * Discards the local orphan branch in favour of the same-named branch that
     * already exists on the remote, keeping the local branch in sync with it.
     *
     * The branch is added to the branches fetched from the remote first: a single-branch
     * clone, which a shallow one is too, fetches no other branch, and Git does not
     * accept a branch it does not fetch as the upstream of a local one.
     */
    private fun adoptRemoteBranch(branch: String) {
        repoExecute("git", "remote", "set-branches", "--add", "origin", branch)
        repoExecute("git", "fetch", "origin", "+refs/heads/$branch:refs/remotes/origin/$branch")
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

        /**
         * Clones the tip of the [branch] of the repository with the provided SSH URL
         * in a temporary folder, with only its top-level files checked out.
         *
         * Suits a branch with a large tip of which a change touches only a small part,
         * such as the branch with the documentation of every published version.
         * Further blobs are fetched only for the paths that [checkOutOnly] selects.
         * The commits are made and [pushed][push] as in a full clone.
         *
         * A [branch] that does not exist yet is created and seeded with [initialFiles],
         * which are top-level files.
         *
         * Configures the username and the email of the Git user.
         * See [configureUser] documentation for more information.
         *
         * @throws IllegalArgumentException if SSH URL is a blank string.
         */
        fun cloneSparsely(
            project: Project,
            sshUrl: String,
            user: UserInfo,
            branch: String,
            initialFiles: Map<String, String> = emptyMap(),
        ): Repository {
            require(sshUrl.isNotBlank()) { "SSH URL cannot be an empty string." }

            val repo = Repository(project, sshUrl = sshUrl, user = user, currentBranch = branch)
            var cloned = false
            try {
                // Ask the remote first: an existing branch is cloned alone, while
                // a missing one needs the default branch to start from.
                val branchExists = repo.remoteHasBranch(branch, remote = sshUrl)
                repo.cloneSparsely(branch.takeIf { branchExists })
                repo.configureUser(user)
                if (!branchExists) {
                    repo.createOrphanBranch(branch, initialFiles)
                }
                cloned = true
                return repo
            } finally {
                // The caller cannot close a repository it never gets.
                if (!cloned) {
                    repo.close()
                }
            }
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
