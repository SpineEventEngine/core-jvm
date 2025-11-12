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

package io.spine.gradle.git

import io.spine.gradle.Cli
import io.spine.gradle.fs.LazyTempPath

/**
 * Interacts with a real Git repository.
 *
 * Clones the repository with the provided SSH URL in a temporal folder. Provides
 * functionality to configure a user, checkout branches, commit changes and push them
 * to the remote repository.
 *
 * It is assumed that before using this class an appropriate SSH key that has
 * sufficient rights to perform described above operations was registered
 * in `ssh-agent`.
 *
 * NOTE: This class creates a temporal folder, so it holds resources. For the proper
 * release of resources please use the provided functionality inside a `use` block or
 * call the `close` method manually.
 */
class Repository private constructor(

    /**
     * The GitHub SSH URL to the underlying repository.
     */
    private val sshUrl: String,

    /**
     * Current user configuration.
     *
     * This configuration determines what ends up in author and committer fields of a commit.
     */
    private var user: UserInfo,

    /**
     * Currently checked out branch.
     */
    private var currentBranch: String

) : AutoCloseable {

    /**
     * Path to the temporal folder for a clone of the underlying repository.
     */
    val location = LazyTempPath("repoTemp")

    /**
     * Clones the repository with [the SSH url][sshUrl] into the [temporal folder][location].
     */
    private fun clone() {
        repoExecute("git", "clone", sshUrl, ".")
    }

    /**
     * Executes a command in the [location].
     */
    private fun repoExecute(vararg command: String): String =
        Cli(location.toFile()).execute(*command)

    /**
     * Checks out the branch by its name.
     */
    fun checkout(branch: String) {
        repoExecute("git", "checkout", branch)

        currentBranch = branch
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
     * Pushes local repository to the remote.
     */
    fun push() {
        repoExecute("git", "push")
    }

    override fun close() {
        location.toFile().deleteRecursively()
    }

    companion object Factory {
        /**
         * Clones the repository with the provided SSH URL in a temporal folder.
         * Configures the username and the email of the Git user. See [configureUser]
         * documentation for more information. Performs checkout of the branch in
         * case it was passed. By default, [master][Branch.master] is checked out.
         *
         * @throws IllegalArgumentException if SSH URL is an empty string.
         */
        fun of(sshUrl: String, user: UserInfo, branch: String = Branch.master): Repository {
            require(sshUrl.isNotBlank()) { "SSH URL cannot be an empty string." }

            val repo = Repository(sshUrl, user, branch)
            repo.clone()
            repo.configureUser(user)

            if (branch != Branch.master) {
                repo.checkout(branch)
            }

            return repo
        }
    }
}
