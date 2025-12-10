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

import com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly
import io.spine.gradle.Cli
import io.spine.gradle.fs.LazyTempPath
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.gradle.api.Project

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
 *
 * @property project The Gradle project in which context the repo operations are held.
 * @property sshUrl The GitHub SSH URL to the underlying repository.
 * @property user Current user configuration.
 *   This configuration determines what ends up in the `author` and `committer` fields of a commit.
 * @property currentBranch The currently checked-out branch.
 */
class Repository private constructor(
    private val project: Project,
    private val sshUrl: String,
    private var user: UserInfo,
    private var currentBranch: String,
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
     */
    fun checkout(branch: String) {
        repoExecute("git", "checkout", branch)
        repoExecute("git", "pull")

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
         * Clones the repository with the provided SSH URL in a temporal folder.
         *
         * Configures the username and the email of the Git user.
         * See [configureUser] documentation for more information.
         *
         * Performs checkout of the branch in case it was passed.
         * By default, [master][Branch.master] is checked out.
         *
         * @throws IllegalArgumentException if SSH URL is an empty string.
         */
        fun clone(
            project: Project,
            sshUrl: String,
            user: UserInfo,
            branch: String = Branch.master,
        ): Repository {
            require(sshUrl.isNotBlank()) { "SSH URL cannot be an empty string." }

            val repo = Repository(project, sshUrl, user, branch)
            repo.clone()
            repo.configureUser(user)

            if (branch != Branch.master) {
                repo.checkout(branch)
            }

            return repo
        }
    }
}

/**
 * Executes a given operation with retries using exponential backoff strategy.
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
