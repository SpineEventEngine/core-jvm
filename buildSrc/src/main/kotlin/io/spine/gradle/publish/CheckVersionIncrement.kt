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

package io.spine.gradle.publish

import io.spine.gradle.VersionComparator
import io.spine.gradle.VersionGradleFile
import io.spine.gradle.repo.Repository
import java.net.URI
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * A task that verifies the project version is fit to be published.
 *
 * Two independent checks run:
 *
 *  1. [checkIncrementedAgainstBase] — inside the dedicated `Version Guard` workflow, the
 *     project [version] must be strictly greater than the version declared by
 *     `version.gradle.kts` on the PR's base branch. This is deterministic and
 *     network-independent: it catches a behavior-changing PR that forgot to bump, and two
 *     parallel PRs that bumped to the same value, regardless of what is (or is not yet)
 *     published.
 *  2. [checkNotPublished] — the [version] must not already exist in the target Maven
 *     repository, so a publication cannot overwrite an immutable artifact.
 *
 * The two checks are complementary; neither subsumes the other.
 */
open class CheckVersionIncrement : DefaultTask() {

    /**
     * The Maven repository in which to look for published artifacts.
     *
     * We check both the `releases` and `snapshots` repositories. Artifacts in either of these repos
     * may not be overwritten.
     */
    @Input
    lateinit var repository: Repository

    @Input
    val version: String = project.version as String

    @TaskAction
    fun checkVersion() {
        checkIncrementedAgainstBase()
        checkNotPublished()
    }

    /**
     * Verifies that the project [version] is strictly greater than the version declared by
     * `version.gradle.kts` on the pull request's base branch.
     *
     * The comparison reads the base branch tip with `git show origin/<base>:version.gradle.kts`,
     * so it runs **only inside the dedicated `Version Guard` workflow** — the one context that
     * fetches the base ref and signals it via the `VERSION_GUARD` environment variable (see
     * [IncrementGuard.shouldCompareToBase]). Every other build skips it: a shallow CI checkout
     * (e.g. the Ubuntu/Windows builds, which pull this task in via `publishToMavenLocal`) has
     * no base ref to read, and local publishes are not pull requests. Those rely on
     * [checkNotPublished] instead.
     *
     * Within the `Version Guard` workflow, failure modes are deliberately asymmetric:
     *  - base ref unresolvable — **fail closed** (a workflow misconfiguration must not pass
     *    silently);
     *  - `version.gradle.kts` absent on base — treated as a newly introduced file (**pass**);
     *  - the publishing-version property cannot be identified — **skip** with a warning,
     *    leaving [checkNotPublished] as the remaining guard, rather than blocking every PR in
     *    a repository whose `version.gradle.kts` uses an unrecognized shape.
     */
    private fun checkIncrementedAgainstBase() {
        val baseRef = System.getenv("GITHUB_BASE_REF")
        if (!IncrementGuard.shouldCompareToBase(underVersionGuard(), baseRef)) {
            logger.info(
                "Skipping the base-branch increment comparison: it runs only inside the " +
                    "`Version Guard` workflow, which fetches the base branch. " +
                    "`checkNotPublished` remains the active guard here."
            )
            return
        }
        val baseVersion = baseVersionToCompare(
            checkNotNull(baseRef) { "`shouldCompareToBase` guarantees a non-blank base ref." }
        )
        if (baseVersion != null && VersionComparator.compare(version, baseVersion) <= 0) {
            throw GradleException(
                """
                The project version `$version` is not greater than the base branch version
                `$baseVersion` (base `$baseRef`).

                A pull request that merges into `$baseRef` must increment the version in
                `${VersionGradleFile.NAME}`. Publishing runs on every push to the base branch,
                so a non-incremented version would collide with the already-published artifact.

                Bump the version (e.g. run `/bump-version`) and push again.

                To disable this check, run Gradle with `-x $name`.
                """.trimIndent()
            )
        }
    }

    /**
     * Tells whether the build runs inside the dedicated `Version Guard` workflow.
     *
     * That workflow fetches the base branch before invoking this task and signals it by
     * setting the `VERSION_GUARD` environment variable to `true`. The variable is the
     * authoritative marker that `origin/<base>` is present, so the base-branch comparison
     * may run; see [IncrementGuard.shouldCompareToBase].
     */
    private fun underVersionGuard(): Boolean =
        "true".equals(System.getenv("VERSION_GUARD"))

    /**
     * Resolves the base-branch publishing version to compare [version] against, or `null`
     * when the comparison does not apply.
     *
     * Returns `null` (skipping the check) when the publishing-version property cannot be
     * identified in the working-tree `version.gradle.kts`, or when the base branch has no
     * comparable value (the file is absent or newly introduced). Throws via
     * [VersionGradleFile.contentInBase] when the base ref itself cannot be resolved.
     */
    private fun baseVersionToCompare(baseRef: String): String? {
        val headContent = VersionGradleFile.contentUnder(project.rootDir)
        val key = headContent?.let { VersionGradleFile.keyForValue(it, version) }
        if (key == null) {
            logger.warn(
                "Could not identify the publishing-version property matching `$version` in " +
                    "`${VersionGradleFile.NAME}`; skipping the base-branch increment check."
            )
            return null
        }
        val baseContent = VersionGradleFile.contentInBase(project.rootDir, baseRef)
        val baseVersion = baseContent?.let { VersionGradleFile.valueForKey(it, key) }
        if (baseVersion == null) {
            logger.info(
                "No comparable `$key` in `${VersionGradleFile.NAME}` on base `$baseRef` " +
                    "(absent or newly introduced); skipping the base-branch increment check."
            )
        }
        return baseVersion
    }

    /**
     * Verifies that the current [version] has not been published to the target Maven
     * repository yet.
     *
     * Both the `releases` and `snapshots` repositories are checked; artifacts in either
     * may not be overwritten.
     */
    private fun checkNotPublished() {
        val artifact = "${project.artifactPath()}/${MavenMetadata.FILE_NAME}"
        val snapshots = repository.target(snapshots = true)
        checkInRepo(snapshots, artifact)

        if (!repository.hasOneTarget()) {
            checkInRepo(repository.target(snapshots = false), artifact)
        }
    }

    private fun checkInRepo(repoUrl: String, artifact: String) {
        val metadata = fetch(repoUrl, artifact)
        val versions = metadata?.versioning?.versions
        val versionExists = versions?.contains(version) ?: false
        if (versionExists) {
            throw GradleException(
                    """
                    The version `$version` is already published to the Maven repository `$repoUrl`.
                    Try incrementing the library version.
                    All available versions are: ${versions.joinToString(separator = ", ")}.

                    To disable this check, run Gradle with `-x $name`.
                    """.trimIndent()
            )
        }
    }

    private fun fetch(repository: String, artifact: String): MavenMetadata? {
        val url = URI.create("$repository/$artifact").toURL()
        return MavenMetadata.fetchAndParse(url)
    }

    private fun Project.artifactPath(): String {
        val group = this.group as String
        val name = "${artifactPrefix()}${this.name}"

        val pathElements = ArrayList(group.split('.'))
        pathElements.add(name)
        val path = pathElements.joinToString(separator = "/")
        return path
    }

    /**
     * Returns the artifact prefix used for the publishing of this project.
     *
     * All current Spine modules should be using `SpinePublishing`.
     * Therefore, the corresponding extension should be present in the root project.
     * However, just in case, we define the "standard" prefix here as well.
     *
     * This value MUST be the same as defined by the defaults in `SpinePublishing`.
     */
    private fun Project.artifactPrefix(): String {
        val ext = rootProject.extensions.findByType(SpinePublishing::class.java)
        val result = ext?.artifactPrefix ?: SpinePublishing.DEFAULT_PREFIX
        return result
    }
}
