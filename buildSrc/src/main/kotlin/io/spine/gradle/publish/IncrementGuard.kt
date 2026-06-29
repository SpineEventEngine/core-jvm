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

import io.spine.gradle.Build
import io.spine.gradle.SpineTaskGroup
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal

/**
 * Gradle plugin that adds a [CheckVersionIncrement] task verifying that the
 * project version was incremented before its artifacts are published.
 *
 * The task — named `checkVersionIncrement` — is run directly by the `Version Guard`
 * CI workflow and before any `publishToMavenLocal` task. It is deliberately kept out
 * of the `check` lifecycle, and actually executes only when the verification is
 * meaningful; see [apply].
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

        /**
         * Tells whether the [CheckVersionIncrement] action must actually run for
         * the current build.
         *
         * The increment is verified in two situations:
         *  1. [ciPullRequest] — a CI pull request that must check the version
         *     (see [shouldCheckVersion]); or
         *  2. a local build (not [onCi]) that is going to publish to Maven Local
         *     ([localPublish]).
         *
         * CI pushes and tag builds that publish to Maven Local — e.g. to feed
         * integration tests — deliberately skip the check, so that re-publishing
         * an already released version does not fail them.
         */
        internal fun mustVerify(
            ciPullRequest: Boolean,
            onCi: Boolean,
            localPublish: Boolean,
        ): Boolean = ciPullRequest || (!onCi && localPublish)

        /**
         * Tells whether [CheckVersionIncrement] should compare the project version against
         * the base branch.
         *
         * The comparison reads `origin/<base>:version.gradle.kts`, so it needs the base
         * branch to have been fetched. Only the dedicated `Version Guard` workflow fetches it
         * and sets the `VERSION_GUARD` environment variable, which [CheckVersionIncrement]
         * passes here as the [underVersionGuard] flag; the workflow runs for pull requests, so
         * a non-blank [baseRef] is required as well.
         *
         * Every other CI build (e.g. the Ubuntu and Windows builds) pulls the check into the
         * task graph through `publishToMavenLocal` but runs a shallow checkout without the
         * base ref. Such builds report `underVersionGuard = false`, so the comparison is
         * skipped and `checkNotPublished` stays the guard — otherwise the comparison would
         * fail closed on `origin/<base>` and break every pull request.
         */
        internal fun shouldCompareToBase(underVersionGuard: Boolean, baseRef: String?): Boolean =
            underVersionGuard && !baseRef.isNullOrBlank()

        /**
         * Tells whether [tasks] contains a Maven Local publishing task that
         * belongs to the given [project].
         *
         * The scan is limited to [project]'s own publications so that, in a
         * multi-project build, a sibling module's `publishToMavenLocal` does not
         * trigger this module's check — which would verify an unrelated version.
         */
        internal fun localPublishPlanned(tasks: Iterable<Task>, project: Project): Boolean =
            tasks.any { it is PublishToMavenLocal && it.project == project }
    }

    /**
     * Adds the [CheckVersionIncrement] task to the [target] project and makes every
     * `publishToMavenLocal` task depend on it, so that a local publish — used by
     * integration tests that consume artifacts from `~/.m2` — cannot overwrite an
     * already published version.
     *
     * The CI pull-request increment check is driven separately by the `Version Guard`
     * workflow (`increment-guard.yml`), which invokes `checkVersionIncrement` by name after
     * fetching the base branch and setting `VERSION_GUARD` — the signal that gates
     * [CheckVersionIncrement]'s strict base-branch comparison (see [shouldCompareToBase]).
     * The task is intentionally not wired into the `check` lifecycle: the version check
     * belongs to the publishing path, not to generic `check` runs. A build that still pulls
     * the task in through `publishToMavenLocal` (e.g. the Ubuntu/Windows CI builds, which
     * publish locally to feed integration tests) stays green, because the base comparison —
     * which reads `origin/<base>` and would otherwise fail closed on a shallow checkout — is
     * skipped outside the `Version Guard` workflow.
     *
     * The task is always created and wired, but its action runs only when:
     *  1. the build is a GitHub Actions pull request targeting a default
     *     (`master` or `main`) or a release-line (e.g. `2.x-jdk8-master`) branch; or
     *  2. the build runs locally (outside CI) and is going to publish artifacts
     *     to Maven Local.
     *
     * It is the responsibility of a branch that aims to merge into a default
     * (or otherwise protected) branch to bump the version. Auxiliary branches do not
     * deal with the versions in the release cycle, so pull requests targeting them,
     * direct pushes, and tag builds do not run the check. In particular, the
     * Maven Local guard is restricted to local builds: re-building `master`,
     * creating git tags, and other CI jobs that publish locally (e.g. to feed
     * integration tests) keep succeeding even though their version is already
     * published. Ordinary local builds that do not publish stay free from the
     * network-bound version check as well.
     */
    override fun apply(target: Project) {
        val tasks = target.tasks
        val checkVersion = tasks.register(taskName, CheckVersionIncrement::class.java) {
            group = SpineTaskGroup.name
            description = "Verifies that the project version was incremented before publishing"
            repository = CloudArtifactRegistry.repository
            onlyIf {
                mustVerify(shouldCheckVersion(), Build.ci, it.publishesToMavenLocal())
            }
        }

        // The CI pull-request increment check is run by the `Version Guard` workflow,
        // which calls `checkVersionIncrement` directly after fetching the base branch.
        // It is intentionally not a dependency of `check`: that would run it in every
        // `./gradlew build` (e.g. the Ubuntu/Windows CI builds), where `origin/<base>`
        // is not fetched and the fail-closed base comparison would break the build.

        // Verify before publishing to Maven Local: integration tests in this and
        // sibling projects consume the freshly published artifacts from `~/.m2`, so a
        // non-incremented version would let them pick up a stale artifact.
        tasks.withType(PublishToMavenLocal::class.java).configureEach {
            dependsOn(checkVersion)
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

/**
 * Tells whether the current build is going to publish this task's project to
 * Maven Local.
 *
 * Integration tests in this and sibling projects consume freshly built artifacts
 * from `~/.m2`. Publishing them under a version that already exists would let those
 * tests pick up a stale artifact, so the version increment must be verified before
 * any local publication runs.
 *
 * Only this task's own project is considered: a sibling module's local publish in
 * the same invocation must not trigger this module's check. The predicate is
 * evaluated lazily as a task `onlyIf` spec, by which point the execution
 * [task graph][org.gradle.api.execution.TaskExecutionGraph] is fully populated.
 */
private fun Task.publishesToMavenLocal(): Boolean =
    IncrementGuard.localPublishPlanned(project.gradle.taskGraph.allTasks, project)
