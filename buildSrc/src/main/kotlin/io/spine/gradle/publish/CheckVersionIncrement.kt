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

package io.spine.gradle.publish

import io.spine.gradle.VersionComparator
import io.spine.gradle.VersionGradleFile
import io.spine.gradle.repo.Repository
import java.net.URI
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

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
 *     repository for any of the [artifacts][artifactPaths] the project publishes, so
 *     a publication cannot overwrite an immutable artifact.
 *
 * The two checks are complementary; neither subsumes the other.
 *
 * Neither the task action nor the `onlyIf` spec that gates it may access
 * [project][org.gradle.api.Task.getProject]: Gradle deprecates that at execution time, and
 * the configuration cache does not support it. Therefore, [version] is captured when the
 * task is created, [IncrementGuard] sets [rootDir] and [artifactPaths] when it registers
 * the task, and it sets [publishesToMavenLocal] once the task graph is ready.
 */
@DisableCachingByDefault(because = "Queries a remote Maven repository and produces no outputs.")
abstract class CheckVersionIncrement : DefaultTask() {

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

    /**
     * The root directory of the build, which holds `version.gradle.kts`.
     *
     * Only its location matters — the task reads `version.gradle.kts` and runs `git` there.
     * Hence, it is not an [input directory][org.gradle.api.tasks.InputDirectory], which
     * would fingerprint the whole build tree.
     */
    @get:Internal
    abstract val rootDir: DirectoryProperty

    /**
     * The paths to the artifacts of the project in a Maven repository, such as
     * `io/spine/spine-base`, one per Maven publication of the project.
     *
     * Empty for a project that publishes nothing.
     */
    @get:Input
    abstract val artifactPaths: SetProperty<String>

    /**
     * Tells whether the build is going to publish this task's project to Maven Local.
     *
     * Integration tests in this and sibling projects consume freshly built artifacts
     * from `~/.m2`. Publishing them under a version that already exists would let those
     * tests pick up a stale artifact, so a local build verifies the version increment
     * before any local publication of the project runs.
     *
     * Only this task's own project counts: a sibling module's local publish in the same
     * invocation must not trigger this module's check (see
     * [IncrementGuard.localPublishPlanned]).
     *
     * [IncrementGuard] scans the task graph once it is ready and sets this property, which
     * the `onlyIf` spec it adds to the task then reads. The value affects only whether
     * the task runs, not what it verifies, so it is not an [input][Input].
     */
    @get:Internal
    abstract val publishesToMavenLocal: Property<Boolean>

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
        val root = rootDir.get().asFile
        val headContent = VersionGradleFile.contentUnder(root)
        val key = headContent?.let { VersionGradleFile.keyForValue(it, version) }
        if (key == null) {
            logger.warn(
                "Could not identify the publishing-version property matching `$version` in " +
                    "`${VersionGradleFile.NAME}`; skipping the base-branch increment check."
            )
            return null
        }
        val baseContent = VersionGradleFile.contentInBase(root, baseRef)
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
     * repository yet, for any of the [artifactPaths].
     *
     * Both the `releases` and `snapshots` repositories are checked; artifacts in either
     * may not be overwritten.
     */
    private fun checkNotPublished() {
        val paths = artifactPaths.get()
        if (paths.isEmpty()) {
            logger.info(
                "The project has no Maven publications; " +
                    "skipping the check for an already published version."
            )
            return
        }
        paths.forEach(::checkArtifactNotPublished)
    }

    /**
     * Verifies that the current [version] of the artifact at the given [path] has not been
     * published to the target Maven repository yet.
     *
     * Each repository is checked as soon as its metadata is fetched, so a failure to reach
     * one repository does not hide the version found in another one checked before it.
     *
     * When no repository has the metadata of the artifact, the version cannot be verified,
     * and a warning is logged. This is expected before the first publication of the artifact.
     * For an artifact published before, it means that [path] does not match the published one.
     */
    private fun checkArtifactNotPublished(path: String) {
        val artifact = "$path/${MavenMetadata.FILE_NAME}"
        val repositories = targetRepositories()
        var found = false
        for (repoUrl in repositories) {
            val metadata = fetch(repoUrl, artifact) ?: continue
            found = true
            checkNotListed(path = path, repoUrl = repoUrl, metadata = metadata)
        }
        if (!found) {
            logger.warn(
                "No `${MavenMetadata.FILE_NAME}` is found for `$path` in " +
                    "${repositories.joinToString { "`$it`" }}. Either the artifact has never " +
                    "been published, or the path does not match the published artifact. " +
                    "`$name` cannot tell whether the version `$version` is already published."
            )
        }
    }

    /**
     * Returns the URLs of the target repositories: the `snapshots` one, and
     * the `releases` one unless it is the same destination.
     */
    private fun targetRepositories(): List<String> {
        val snapshots = repository.target(snapshots = true)
        if (repository.hasOneTarget()) {
            return listOf(snapshots)
        }
        return listOf(snapshots, repository.target(snapshots = false))
    }

    /**
     * Throws if the [metadata] of the artifact at the given [path], fetched from [repoUrl],
     * lists the current [version].
     */
    private fun checkNotListed(path: String, repoUrl: String, metadata: MavenMetadata) {
        val versions = metadata.versioning.versions
        if (version in versions) {
            throw GradleException(
                    """
                    The version `$version` of `$path` is already published
                    to the Maven repository `$repoUrl`.
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
}
