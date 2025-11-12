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

@file:Suppress("unused")

package io.spine.gradle.publish

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin which adds a [CheckVersionIncrement] task.
 *
 * The task is called `checkVersionIncrement` inserted before the `check` task.
 */
class IncrementGuard : Plugin<Project> {

    companion object {
        const val taskName = "checkVersionIncrement"
    }

    /**
     * Adds the [CheckVersionIncrement] task to the project.
     *
     * The task is created anyway, but it is enabled only if:
     *  1. The project is built on GitHub CI, and
     *  2. The job is a pull request.
     *
     * The task only runs on non-master branches on GitHub Actions.
     * This is done to prevent unexpected CI fails when re-building `master` multiple times,
     * creating git tags, and in other cases that go outside the "usual" development cycle.
     */
    override fun apply(target: Project) {
        val tasks = target.tasks
        tasks.register(taskName, CheckVersionIncrement::class.java) {
            repository = CloudArtifactRegistry.repository
            tasks.getByName("check").dependsOn(this)

            if (!shouldCheckVersion()) {
                logger.info(
                    "The build does not represent a GitHub Actions feature branch job, " +
                            "the `checkVersionIncrement` task is disabled."
                )
                this.enabled = false
            }
        }
    }

    /**
     * Returns `true` if the current build is a GitHub Actions build which represents a push
     * to a feature branch.
     *
     * Returns `false` if the associated reference is not a branch (e.g., a tag) or if it has
     * the name which ends with `master` or `main`.
     *
     * For example, on the following branches the method would return `false`:
     *
     * 1. `master`.
     * 2. `main`.
     * 3. `2.x-jdk8-master`.
     * 4. `2.x-jdk8-main`.
     *
     * @see <a href="https://docs.github.com/en/free-pro-team@latest/actions/reference/environment-variables">
     *     List of default environment variables provided for GitHub Actions builds</a>
     */
    private fun shouldCheckVersion(): Boolean {
        val event = System.getenv("GITHUB_EVENT_NAME")
        val reference = System.getenv("GITHUB_REF")
        if (event != "push" || reference == null) {
            return false
        }
        val branch = branchName(reference)
        return when {
            branch == null -> false
            branch.endsWith("master") -> false
            branch.endsWith("main") -> false
            else -> true
        }
    }

    private fun branchName(gitHubRef: String): String? {
        val matches = Regex("refs/heads/(.+)").matchEntire(gitHubRef)
        val branch = matches?.let { it.groupValues[1] }
        return branch
    }
}
