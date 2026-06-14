/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import io.spine.gradle.SpineTaskGroup
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

        /**
         * Tells whether the version increment must be verified for the given
         * GitHub Actions event and the base branch of the pull request.
         *
         * The version is guarded only for pull requests targeting a default or
         * a release-line branch, i.e. a branch with the name ending with `master`
         * or `main`. For example: `master`, `main`, `2.x-jdk8-master`, `2.x-jdk8-main`.
         */
        internal fun shouldCheckVersion(event: String?, baseBranch: String?): Boolean {
            if (event != "pull_request" || baseBranch == null) {
                return false
            }
            return baseBranch.endsWith("master") || baseBranch.endsWith("main")
        }
    }

    /**
     * Adds the [CheckVersionIncrement] task to the project.
     *
     * The task is created anyway, but it is enabled only if:
     *  1. The project is built on GitHub CI, and
     *  2. The job is a pull request targeting a default (`master` or `main`) or
     *     a release-line (e.g. `2.x-jdk8-master`) branch.
     *
     * It is the responsibility of a branch which aims to merge into a default
     * (or otherwise protected) branch to bump the version. Auxiliary branches do not
     * deal with the versions in the release cycle, so pull requests targeting them,
     * direct pushes, and tag builds do not run the check. This also prevents unexpected
     * CI fails when re-building `master` multiple times, creating git tags, and in other
     * cases that go outside the "usual" development cycle.
     */
    override fun apply(target: Project) {
        val tasks = target.tasks
        tasks.register(taskName, CheckVersionIncrement::class.java) {
            group = SpineTaskGroup.name
            description = "Verifies that the project version was incremented before publishing"
            repository = CloudArtifactRegistry.repository
            tasks.getByName("check").dependsOn(this)

            if (!shouldCheckVersion()) {
                logger.info(
                    "The build does not represent a GitHub Actions pull request job " +
                            "targeting a default or a release-line branch, " +
                            "the `checkVersionIncrement` task is disabled."
                )
                this.enabled = false
            }
        }
    }

    /**
     * Returns `true` if the current build is a GitHub Actions build of a pull request
     * targeting a default (`master` or `main`) or a release-line branch,
     * such as `2.x-jdk8-master`.
     *
     * Returns `false` for all other builds, including direct pushes, tag builds,
     * and pull requests targeting auxiliary branches.
     *
     * @see <a href="https://docs.github.com/en/free-pro-team@latest/actions/reference/environment-variables">
     *     List of default environment variables provided for GitHub Actions builds</a>
     */
    private fun shouldCheckVersion(): Boolean {
        val event = System.getenv("GITHUB_EVENT_NAME")
        val baseBranch = System.getenv("GITHUB_BASE_REF")
        return shouldCheckVersion(event, baseBranch)
    }
}
