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

import io.spine.gradle.SpineTaskGroup
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Tells whether the Maven publications of the build are already published.
 *
 * The `Publish` workflow runs this task before it builds anything. Every version is
 * immutable in the registries, which reject any overwrite, so a run can upload only
 * a version that nobody has uploaded before. The task looks for the files of each
 * publication in each of its destinations — the remote repositories its project
 * publishes to, such as Artifact Registry and GitHub Packages — and decides:
 *
 *  - no file of any publication is there — `unpublished`, the run publishes;
 *  - every publication is complete in every destination, and every copy of the SBOMs
 *    published next to the artifacts names the [commit] — `published`: an earlier run of
 *    the same commit uploaded the version, e.g. a duplicate run of one push, and this
 *    run has nothing to do;
 *  - any other state fails the task, before a single upload. A gap anywhere — a file
 *    or a whole publication missing from a destination — is left over from a failed
 *    run, or from GitHub Packages pruning the version. A version published from another
 *    commit, in any destination, is a collision. A version with an SBOM copy naming no
 *    commit, or with no commit to compare, cannot be confirmed as this run's. No run can
 *    publish any of these.
 *
 * The state is written to the [stateFile] as `unpublished` or `published`.
 *
 * A publication is complete in a destination when the destination lists its version in
 * `maven-metadata.xml`, and has each of its [files]. The Gradle module metadata
 * (`.module`) is not among them, as no public API tells at configuration time whether
 * a publication has it. It is required only to be in all destinations of a publication,
 * or in none.
 *
 * Repositories not served over HTTP(S), such as a directory on disk, are not searched:
 * a CI build starts with them empty, so they never hold a version published before.
 * GitHub Packages is read with a token (see [gitHubPackages]); any other repository is
 * read anonymously, so a repository that needs credentials to be read fails the task.
 *
 * An SBOM is the evidence of the commit because it records the revision it was built
 * from (see [PublicationSbom]), and no other published file does. A publication without
 * an SBOM, such as a Gradle plugin marker, takes no part in the decision about the commit.
 */
@UntrackedTask(because = "The state lives in remote repositories, not in local outputs.")
abstract class PublicationState : DefaultTask() {

    /**
     * The versions of the publications to look up, keyed by the path to the artifact in
     * a Maven repository, such as `io/spine/spine-base`.
     */
    @get:Input
    abstract val publications: MapProperty<String, String>

    /**
     * The names of the files of each publication, keyed by the path to the artifact.
     *
     * The names are those of the artifacts and the POM, such as
     * `spine-base-2.0.0.jar` or `spine-base-2.0.0.pom`.
     */
    @get:Input
    abstract val files: MapProperty<String, List<String>>

    /**
     * The URLs of the destinations of each publication, keyed by the path to the artifact.
     */
    @get:Input
    abstract val destinations: MapProperty<String, List<String>>

    /**
     * The URLs of the [destinations] that are GitHub Packages repositories.
     *
     * GitHub Packages serves nothing without a token, so the task reads these with
     * the one in the `GITHUB_TOKEN` environment variable, and sends it nowhere else.
     */
    @get:Input
    abstract val gitHubPackages: SetProperty<String>

    /**
     * The commit being published.
     *
     * Absent outside GitHub Actions, where `GITHUB_SHA` is not set. In that case,
     * a version that is already published cannot be attributed to any commit, and
     * fails the task.
     */
    @get:Input
    @get:Optional
    abstract val commit: Property<String>

    /**
     * The file to write the state to.
     *
     * The task deletes the file when it starts, so a failed task leaves none.
     */
    @get:OutputFile
    abstract val stateFile: RegularFileProperty

    @TaskAction
    fun lookUp() {
        val file = stateFile.get().asFile
        file.delete()
        val versions = publications.get()
        if (versions.isEmpty()) {
            logger.warn(
                "No project publishes to a remote repository," +
                        " so there is no publication to look up."
            )
        }
        val inspector = Inspector(
            files = files.get(),
            destinations = destinations.get(),
            gitHubPackages = gitHubPackages.get(),
            gitHubAuthorization = gitHubAuthorization()
        )
        val findings = inspector.inspectAll(versions)
        findings.forEach { logger.lifecycle(it.describe()) }
        val currentCommit = commit.orNull
        val decision = Verdict.of(findings = findings, commit = currentCommit)
        val state = decision.state ?: throw GradleException(
            explain(decision = decision, findings = findings, commit = currentCommit)
        )
        file.parentFile.mkdirs()
        file.writeText("$state\n")
        logger.lifecycle("The publication state is `$state`.")
    }

    /**
     * Obtains the `Authorization` header for reading the [gitHubPackages], if there are
     * any, with the token in the `GITHUB_TOKEN` environment variable.
     *
     * @throws GradleException if there is a GitHub Packages repository to read, but
     *   no token.
     */
    private fun gitHubAuthorization(): String? {
        val repositories = gitHubPackages.get()
        if (repositories.isEmpty()) {
            return null
        }
        val token = System.getenv("GITHUB_TOKEN")
        if (token.isNullOrBlank()) {
            throw GradleException(
                "Reading the GitHub Packages repository " +
                        repositories.joinToString { "`$it`" } +
                        " requires a token in the `GITHUB_TOKEN` environment variable."
            )
        }
        val credentials = "${GitHubPackages.actor()}:$token"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    private fun explain(decision: Verdict, findings: List<Finding>, commit: String?): String {
        val reason = when (decision) {
            Verdict.PARTIAL -> explainPartial(findings)
            Verdict.COLLISION -> {
                val others = findings.flatMap { it.commits }.toSet() - commit
                val from = if (others.size == 1) "another commit" else "other commits"
                """
                The version is published from $from: ${others.joinToString { "`$it`" }},
                while this build publishes `$commit`.
                """.trimIndent()
            }
            Verdict.UNCONFIRMED -> if (commit == null) {
                "The version is published, but this build cannot tell which commit" +
                        " it publishes: `GITHUB_SHA` is not set."
            } else {
                "The version is published, but a published SBOM does not name the commit" +
                        " it was published from."
            }
            Verdict.UNPUBLISHED, Verdict.PUBLISHED ->
                error("The verdict `$decision` needs no explanation.")
        }
        return reason + "\n" +
                """
                The registries reject overwrites, so no run can publish this version.
                Bump the version in `version.gradle.kts`: the run of that merge publishes
                the new version.
                """.trimIndent()
    }

    private fun explainPartial(findings: List<Finding>): String {
        val incomplete = findings.filter { it.presence != Presence.COMPLETE }
        val gaps = incomplete.flatMap { finding ->
            when (finding.presence) {
                Presence.ABSENT -> listOf("${finding.label}: the whole publication")
                Presence.PARTIAL, Presence.COMPLETE ->
                    finding.gaps.map { "${finding.label}: $it" }
            }
        }
        val gitHub = gitHubPackages.get()
        val gitHubOnly = incomplete.all { finding ->
            finding.presence == Presence.PARTIAL && finding.incomplete.all { it in gitHub }
        }
        val pruned = if (gitHubOnly) {
            "\nEvery gap is in GitHub Packages, which keeps only the latest snapshot versions" +
                    " of a package. If the version is an old one, it was pruned from there," +
                    " and the failure needs no action; if it is the latest, follow the advice" +
                    " below."
        } else {
            ""
        }
        return "The version is partially published. Missing:\n" +
                gaps.joinToString(separator = "\n") { " - $it" } +
                pruned
    }

    /**
     * The files of a publication held by one of its destinations.
     *
     * @property destination The URL of the repository.
     * @property listed Tells whether the repository lists the version of the publication.
     * @property present The names of the files of the publication in the repository.
     */
    internal data class Holding(
        val destination: String,
        val listed: Boolean,
        val present: Set<String>
    )

    /**
     * How much of a publication is published.
     */
    internal enum class Presence {

        /**
         * Every destination lists the version and has every file, and the Gradle module
         * metadata is in all of them or in none.
         */
        COMPLETE,

        /** No destination lists the version or has any file. */
        ABSENT,

        /**
         * Anything else: a file or the listing missing from some destination, or
         * the module metadata in some destinations only.
         */
        PARTIAL
    }

    /**
     * The published state of a Maven publication.
     *
     * @property path The path to the artifact in a Maven repository.
     * @property version The version of the publication.
     * @property presence How much of the publication is published.
     * @property commits The commits named by the copies of the SBOM of the publication in
     *   its destinations, if it is [complete][Presence.COMPLETE]. Empty otherwise, or if it
     *   has no SBOM.
     * @property gaps What is missing from each destination, if anything.
     * @property incomplete The destinations missing anything of the publication.
     * @property unconfirmed Tells whether the publication has an SBOM, and a copy of it in
     *   some destination names no commit, as one built outside GitHub Actions does.
     */
    internal data class Finding(
        val path: String,
        val version: String,
        val presence: Presence,
        val commits: Set<String> = emptySet(),
        val gaps: List<String> = emptyList(),
        val incomplete: Set<String> = emptySet(),
        val unconfirmed: Boolean = false
    ) {

        /**
         * The coordinates of the publication for the messages of the task.
         */
        val label: String
            get() = "`$path:$version`"

        /**
         * Describes the finding for the build log.
         */
        fun describe(): String = when (presence) {
            Presence.ABSENT -> "$label is not published."
            Presence.PARTIAL -> "$label is partially published; missing: ${gaps.joinToString()}."
            Presence.COMPLETE -> {
                val from = commits.joinToString { "`$it`" }
                when {
                    commits.isEmpty() -> "$label is published, with no SBOM naming its commit."
                    unconfirmed -> "$label is published from $from, but a copy of its SBOM" +
                            " names no commit."
                    else -> "$label is published from $from."
                }
            }
        }
    }

    /**
     * The decision about the publications of a build.
     *
     * @property state The state written for the `Publish` workflow, or `null` if the
     *   version cannot be published, in which case the task fails.
     */
    internal enum class Verdict(val state: String?) {

        /** No publication is published, or there is none to look up. */
        UNPUBLISHED("unpublished"),

        /**
         * Every publication is complete in every destination, and every copy of the SBOMs
         * names the commit being published.
         */
        PUBLISHED("published"),

        /**
         * Some publications, or some of their files, are missing from some destinations,
         * and the rest is published.
         */
        PARTIAL(null),

        /** Every publication is published, but a copy of an SBOM names another commit. */
        COLLISION(null),

        /**
         * Every publication is published, but the commit cannot be compared: the commit
         * being published is unknown, or a copy of an SBOM names no commit.
         */
        UNCONFIRMED(null);

        companion object {

            /**
             * Decides on the verdict for the [findings] of a build that publishes
             * the given [commit].
             *
             * @param findings The published states of all publications of the build.
             * @param commit The commit being published, or `null` if it is unknown.
             */
            fun of(findings: Collection<Finding>, commit: String?): Verdict {
                val commits = findings.flatMap { it.commits }.toSet()
                return when {
                    findings.all { it.presence == Presence.ABSENT } -> UNPUBLISHED
                    findings.any { it.presence != Presence.COMPLETE } -> PARTIAL
                    commit == null || commits.isEmpty() -> UNCONFIRMED
                    commits != setOf(commit) -> COLLISION
                    findings.any { it.unconfirmed } -> UNCONFIRMED
                    else -> PUBLISHED
                }
            }
        }
    }

    companion object {

        /**
         * The name of the task, registered in the root project.
         */
        const val taskName = "publicationState"

        private const val stateFilePath = "publication/state.txt"

        /**
         * Matches the revision in the source information that the SPDX Gradle Plugin
         * writes for a module of this build: `git+<uri>@<revision>#<name>[<path>]`.
         *
         * The revision is a full commit hash: 40 hexadecimal digits of SHA-1, or 64 of SHA-256.
         */
        private val scmRevision = Regex("""git\+[^"\s]+?@([0-9a-f]{64}|[0-9a-f]{40})#""")

        /**
         * Obtains the revisions named in the source information of the given SBOM text.
         *
         * A revision that is not a full commit hash, such as the `unknown` written for
         * a build outside GitHub Actions, is not matched.
         */
        internal fun sbomRevisions(text: String): Set<String> =
            scmRevision.findAll(text).map { it.groupValues[1] }.toSet()

        /**
         * Decides how much of a publication is published, from the [holdings] of its
         * destinations, the [names] of its files, and the name of its [module] metadata.
         */
        internal fun presenceOf(
            holdings: List<Holding>,
            names: List<String>,
            module: String
        ): Presence {
            val modules = holdings.map { module in it.present }.toSet()
            return when {
                holdings.all { !it.listed && it.present.isEmpty() } -> Presence.ABSENT
                holdings.all { it.listed && it.present.containsAll(names) } &&
                        modules.size == 1 -> Presence.COMPLETE
                else -> Presence.PARTIAL
            }
        }

        /**
         * Describes what is missing from each of the [holdings] to complete
         * the publication at the given [path].
         *
         * @return the descriptions of the gaps keyed by the destination, for each
         *   destination missing anything.
         */
        internal fun gapsOf(
            holdings: List<Holding>,
            path: String,
            names: List<String>,
            module: String
        ): Map<String, List<String>> {
            val expected = if (holdings.any { module in it.present }) names + module else names
            return holdings.associate { holding ->
                val metadata = "${holding.destination}/$path/${MavenMetadata.FILE_NAME}"
                val unlisted = if (holding.listed) {
                    emptyList()
                } else {
                    listOf("the version in `$metadata`")
                }
                val absent = (expected - holding.present).map {
                    "`$it` in `${holding.destination}`"
                }
                holding.destination to unlisted + absent
            }.filterValues { it.isNotEmpty() }
        }

        /**
         * Registers the task in the given [root] project, unless it is registered already.
         *
         * The publications are collected from every project of the build once all of
         * them are evaluated, since the publishing handlers configure the coordinates and
         * the artifacts in `afterEvaluate`. They are handed to the task as plain values,
         * so that the task reads the state of no other project while it runs.
         *
         * Call it after [PublicationSbom.registerTasks]: both collect in `projectsEvaluated`
         * callbacks, which run in the order of registration, and this task must see
         * the SBOM artifacts that the other one adds to the publications.
         */
        internal fun registerOn(root: Project) {
            if (root.tasks.names.contains(taskName)) {
                return
            }
            val task = root.tasks.register(taskName, PublicationState::class.java) {
                group = SpineTaskGroup.name
                description = "Tells whether the version of the Maven publications is published"
                commit.set(
                    root.providers.environmentVariable("GITHUB_SHA").filter { it.isNotBlank() }
                )
                stateFile.convention(root.layout.buildDirectory.file(stateFilePath))
            }
            root.gradle.projectsEvaluated {
                task.configure {
                    root.allprojects.forEach { collect(it) }
                }
            }
        }

        /**
         * Adds the publications of the given [project] to the inputs of this task, if
         * the project publishes to a remote repository.
         */
        private fun PublicationState.collect(project: Project) {
            val repositories = project.remoteRepositories()
            if (repositories.isEmpty()) {
                return
            }
            project.mavenPublications().forEach { publication ->
                val path = publication.repositoryPath
                publications.put(path, publication.version)
                files.put(path, publication.fileNames())
                destinations.put(path, repositories.map { it.location() })
            }
            gitHubPackages.addAll(
                repositories
                    .filter { GitHubPackages.isRepository(it) && it.carriesTokenSafely() }
                    .map { it.location() }
            )
        }
    }
}

/**
 * Looks up the files of publications in their destinations.
 *
 * Publications are looked up [in parallel][parallelism], as a build may have dozens of
 * them, and each one takes a request per file per destination.
 *
 * @property files The names of the files of each publication.
 * @property destinations The URLs of the destinations of each publication.
 * @property gitHubPackages The destinations to read with the [gitHubAuthorization].
 * @property gitHubAuthorization The `Authorization` header for reading GitHub Packages.
 */
private class Inspector(
    private val files: Map<String, List<String>>,
    private val destinations: Map<String, List<String>>,
    private val gitHubPackages: Set<String>,
    private val gitHubAuthorization: String?
) {

    /**
     * Looks up the publications of the given versions, keyed by the paths to the artifacts.
     */
    fun inspectAll(versions: Map<String, String>): List<PublicationState.Finding> {
        val pool = Executors.newFixedThreadPool(parallelism)
        try {
            val lookups = versions.map { (path, version) ->
                Callable { inspect(path = path, version = version) }
            }
            return pool.invokeAll(lookups).map { it.result() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun inspect(path: String, version: String): PublicationState.Finding {
        val names = files[path].orEmpty()
        val module = "${path.substringAfterLast('/')}-$version.module"
        val holdings = destinations[path].orEmpty().map { destination ->
            PublicationState.Holding(
                destination = destination,
                listed = lists(destination = destination, path = path, version = version),
                present = (names + module)
                    .filter { has(destination = destination, file = "$path/$version/$it") }
                    .toSet()
            )
        }
        val presence = PublicationState.presenceOf(
            holdings = holdings,
            names = names,
            module = module
        )
        val sbomCopies = if (presence == PublicationState.Presence.COMPLETE) {
            sbomCommits(holdings = holdings, path = path, version = version, names = names)
        } else {
            emptyList()
        }
        val gaps = PublicationState.gapsOf(
            holdings = holdings,
            path = path,
            names = names,
            module = module
        )
        return PublicationState.Finding(
            path = path,
            version = version,
            presence = presence,
            commits = sbomCopies.flatten().toSet(),
            gaps = gaps.values.flatten(),
            incomplete = gaps.keys,
            unconfirmed = sbomCopies.any { it.isEmpty() }
        )
    }

    private fun lists(destination: String, path: String, version: String): Boolean {
        val metadataUrl = url(destination, "$path/${MavenMetadata.FILE_NAME}")
        val metadata = MavenMetadata.fetchAndParse(metadataUrl, authorization(destination))
        return metadata != null && version in metadata.versioning.versions
    }

    private fun has(destination: String, file: String): Boolean =
        RemoteFile.exists(url(destination, file), authorization(destination))

    /**
     * Obtains the commits named by each copy of the SBOM of a complete publication, one
     * set for each of its [holdings].
     *
     * Every copy is read, as each destination may hold a copy uploaded by another run.
     * A copy naming no commit gives an empty set, and so does one that cannot be read.
     *
     * Returns an empty list if the publication has no SBOM.
     */
    private fun sbomCommits(
        holdings: List<PublicationState.Holding>,
        path: String,
        version: String,
        names: List<String>
    ): List<Set<String>> {
        val sbom = names.firstOrNull { it.endsWith(".${PublicationSbom.sbomExtension}") }
            ?: return emptyList()
        return holdings.map { holding ->
            val sbomUrl = url(holding.destination, "$path/$version/$sbom")
            RemoteFile.readText(sbomUrl, authorization(holding.destination))
                ?.let(PublicationState::sbomRevisions)
                ?: emptySet()
        }
    }

    private fun authorization(destination: String): String? =
        gitHubAuthorization.takeIf { destination in gitHubPackages }

    private fun url(repository: String, path: String): URL =
        URI.create("${repository.removeSuffix("/")}/$path").toURL()

    private fun <T> Future<T>.result(): T =
        try {
            get()
        } catch (e: ExecutionException) {
            throw e.cause ?: IllegalStateException(e)
        }

    private companion object {

        /**
         * The number of publications looked up at a time.
         */
        const val parallelism = 4
    }
}

/**
 * The schemes of the URLs of remote repositories.
 */
private val remoteSchemes = setOf("http", "https")

/**
 * Obtains the remote Maven repositories this project publishes to.
 *
 * A repository with no URL is left out, as Gradle would fail to publish to it anyway.
 */
private fun Project.remoteRepositories(): List<MavenArtifactRepository> =
    extensions.findByType(PublishingExtension::class.java)
        ?.repositories
        ?.withType(MavenArtifactRepository::class.java)
        ?.filter { repository ->
            // `getUrl()` returns `null` for an unset URL.
            repository.url != null && repository.url.scheme in remoteSchemes
        }
        .orEmpty()

/**
 * Obtains the URL of this repository with no trailing slash.
 */
private fun MavenArtifactRepository.location(): String = url.toString().removeSuffix("/")

/**
 * Tells whether a token may be sent to this repository: over HTTPS, or to the loopback
 * address of a test.
 */
private fun MavenArtifactRepository.carriesTokenSafely(): Boolean =
    url.scheme == "https" || url.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")

/**
 * Obtains the names of the files of this publication: its artifacts and the POM.
 */
private fun MavenPublication.fileNames(): List<String> {
    val artifactNames = artifacts.map {
        publishedName(classifier = it.classifier, extension = it.extension)
    }
    return (artifactNames + publishedName(classifier = null, extension = "pom")).distinct()
}
