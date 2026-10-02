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
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Writes a manifest of the artifacts published by this build, so that GitHub
 * Actions can attest their build provenance.
 *
 * Usage:
 * ```
 * PublicationChecksums.registerTasks(project, projectsToPublish)
 * ```
 *
 * ## Why Gradle computes the manifest
 *
 * The `actions/attest` action identifies an attested artifact by its digest and
 * by a name, both of which it takes **verbatim** from the manifest — it never
 * re-reads the artifacts. So the manifest must name each artifact exactly as it
 * is published, and cover all of them: an omission silently narrows a signed
 * claim, and a wrong digest signs a claim about the wrong bytes.
 *
 * Neither is obtainable by globbing the build directory. The name of an artifact
 * on disk is derived from the Gradle project name, while the published name uses
 * [the artifact ID][SpinePublishing.artifactPrefix] — `client-2.0.0.jar` is
 * published as `spine-client-2.0.0.jar`. The POM and the Gradle module metadata
 * are not in `build/libs` at all; they are generated under `build/publications`,
 * using the fixed names `pom-default.xml` and `module.json`. Only the publication
 * knows how these map onto published names.
 *
 * ## Why a task per project
 *
 * A task of the root project may not reach into the state of another project.
 * As with [the POM report][io.spine.gradle.report.pom.PomGenerator], a
 * [collector task][registerCollectorIn] is therefore registered in every
 * published project, describing only its own publications, and
 * [an aggregator][registerAggregatorIn] merges their output.
 *
 * Neither task declares inputs or outputs, on purpose: both always run, so the
 * manifest cannot describe a previous state of the build.
 */
internal object PublicationChecksums {

    /**
     * The name of the per-project task registered by [registerCollectorIn].
     */
    const val collectorTaskName = "collectPublicationChecksums"

    /**
     * The name of the task registered by [registerAggregatorIn].
     */
    const val aggregatorTaskName = "publicationChecksums"

    private const val collectorPath = "attestation/checksums.txt"

    private const val aggregatePath = "attestation/subject-checksums.txt"

    /**
     * Separates a digest from the name of its subject, as `sha256sum` writes it.
     *
     * The action reads the name as everything after the first space, dropping one
     * further `*` or space, so the two-space form leaves the name intact.
     */
    private const val digestSeparator = "  "

    /**
     * Registers the [collector][registerCollectorIn] tasks in the given
     * [published] projects, and the [aggregator][registerAggregatorIn] task in
     * [host].
     *
     * [host] is the project in which `spinePublishing { }` was opened. That is
     * the root project of a multi-module build, which is where `publish.yml`
     * expects the manifest. A module configuring the extension for itself also
     * gets an aggregator of its own, describing only that module.
     */
    fun registerTasks(host: Project, published: Set<Project>) {
        val collectors = published.map { registerCollectorIn(it) }
        registerAggregatorIn(host, published, collectors)
    }

    /**
     * Registers the [collectorTaskName] task in the given [project].
     *
     * The task depends on everything that produces a published file: the
     * artifacts of the publications of this project, and the tasks generating
     * the POM and the Gradle module metadata.
     *
     * The task orders itself after `clean` — Gradle does not order the two
     * otherwise, so in a `gradle clean build` invocation a late-running `clean`
     * could delete a freshly written manifest.
     *
     * A version Maven would deploy under a timestamped name is rejected while
     * the project is still being configured, rather than when this task runs.
     * The distributed workflow asks for `publish` and `publicationChecksums` in
     * one invocation, and Gradle may upload before it reaches the collector, so
     * a check inside the task would fail only after the artifacts were already
     * published — leaving exactly the published-but-unattested state the check
     * exists to prevent.
     */
    private fun registerCollectorIn(project: Project): TaskProvider<Task> {
        project.afterEvaluate {
            mavenPublications().forEach { it.rejectMavenSnapshot() }
        }
        return project.tasks.getOrRegister(collectorTaskName) {
            group = SpineTaskGroup.name
            description = "Computes the digests of the artifacts published by " +
                    "the `${project.name}` project"
            mustRunAfter(project.tasks.cleanTask())
            dependsOn(project.provider {
                project.mavenPublications().flatMap { publication ->
                    publication.artifacts.map { it.buildDependencies }
                }
            })
            dependsOn(project.tasks.withType(GenerateMavenPom::class.java))
            dependsOn(project.tasks.withType(GenerateModuleMetadata::class.java))
            doLast {
                val file = collectorOutput(project).get().asFile
                file.parentFile.mkdirs()
                file.writeText(serialize(project.publishedArtifacts()))
            }
        }
    }

    /**
     * Registers the [aggregatorTaskName] task in [host].
     *
     * The task merges the manifests written by the [collectors] into a single
     * file in the `sha256sum` format, which `actions/attest` accepts as its
     * `subject-checksums` input, ordered by subject name so that two runs of the
     * same build produce a file that can be compared line by line.
     *
     * A manifest missing at merge time fails the task. Every collector writes
     * unconditionally, so an absent one means the subject set is incomplete —
     * and an attestation that silently covers less than it appears to is worse
     * than none. Two digests published under one name fail it for the same
     * reason: only one of them can describe what a consumer resolves.
     *
     * The task orders itself after `clean` for the reason given in
     * [registerCollectorIn]: it writes under the build directory that `clean`
     * removes.
     */
    private fun registerAggregatorIn(
        host: Project,
        published: Set<Project>,
        collectors: List<TaskProvider<Task>>
    ) {
        // Resolved outside the task action: reading the layout of another project
        // from `doLast` is the cross-project access this class is arranged to
        // avoid, and it is what makes a task incompatible with the configuration
        // cache. The providers stay lazy, so a project may still reconfigure its
        // build directory afterwards.
        //
        // The list belongs to the project rather than to this call, because the
        // task is registered once while `registerTasks` may run several times —
        // see `getOrRegister`. Were the action to close over one call's projects,
        // a later call adding a module would register its collector and still
        // leave it out of the manifest.
        val sources = host.attestationSources()
        sources += published.map { collectorOutput(it) }
        val target = host.layout.buildDirectory.file(aggregatePath)
        val aggregator = host.tasks.getOrRegister(aggregatorTaskName) {
            group = SpineTaskGroup.name
            description = "Writes the digests of all published artifacts for attestation"
            mustRunAfter(host.tasks.cleanTask())
            doLast {
                val merged = sources
                    .map { it.get().asFile }
                    .onEach {
                        check(it.exists()) {
                            "No checksum manifest at `$it`." +
                                    " The attestation would omit a published module."
                        }
                    }
                    .flatMap { it.readLines() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { it.subjectName() }
                merged.ensureNamesUnique()
                val file = target.get().asFile
                file.parentFile.mkdirs()
                file.writeText(merged.joinToString(separator = "\n", postfix = "\n"))
                logger.lifecycle("Wrote ${merged.size} attestation subject(s) to `$file`.")
            }
        }
        // Applied on every call, including the one that registered the task, so
        // that collectors added by a later call are run before the merge reads
        // the files they write.
        aggregator.configure {
            dependsOn(collectors)
        }
    }

    /**
     * Returns the file under the build directory of [project] in which
     * [the collector task][registerCollectorIn] describes that project.
     */
    private fun collectorOutput(project: Project): Provider<RegularFile> =
        project.layout.buildDirectory.file(collectorPath)

    /**
     * Renders the given artifacts as `sha256sum` records, ordered by name.
     */
    private fun serialize(artifacts: Map<String, String>): String =
        artifacts.entries
            .sortedBy { it.key }
            .joinToString(separator = "\n", postfix = "\n") { (name, digest) ->
                "$digest$digestSeparator$name"
            }

    /**
     * Returns the subject name of this `sha256sum` record.
     */
    private fun String.subjectName(): String = substringAfter(digestSeparator)

    /**
     * Fails unless every record in this list names a distinct subject.
     *
     * Byte-identical records are already gone by this point, so a name occurring
     * twice carries two different digests. Attesting both would state that the
     * same published file is two different things.
     */
    private fun List<String>.ensureNamesUnique() {
        val duplicated = groupBy { it.subjectName() }
            .filterValues { it.size > 1 }
            .keys
        check(duplicated.isEmpty()) {
            "Different digests are published under the same name: " +
                    duplicated.joinToString { "`$it`" } + "."
        }
    }
}

/**
 * Returns the task named [name] in this container, registering it with [init]
 * if it is not there yet.
 *
 * `spinePublishing { }` configures the extension anew on every call, and the
 * same project can be reached by two such calls: the root extension lists a
 * module in [SpinePublishing.modulesWithCustomPublishing], while the module
 * itself opens the extension with `customPublishing = true` — which is what
 * `uber-jar-module.gradle.kts` does. Plain registration fails on the second
 * call, during configuration, so registration has to be idempotent.
 */
private fun TaskContainer.getOrRegister(
    name: String,
    init: Task.() -> Unit
): TaskProvider<Task> =
    if (names.contains(name)) {
        named(name)
    } else {
        register(name, init)
    }

/**
 * Returns the `clean` task of this container, if it has one.
 *
 * Filtering by name alone keeps the lookup lazy. Matching on a task instance
 * would instantiate every registered task in the project just to read its name.
 */
private fun TaskContainer.cleanTask(): TaskCollection<Task> = named { it == "clean" }

/**
 * Returns the manifests the aggregator of this project merges, accumulated
 * across every call of [PublicationChecksums.registerTasks] for it.
 *
 * The list is attached to the project, so that it lives exactly as long as
 * the build does. Holding it in the object registering the tasks would share
 * it between builds, which reuse a Gradle daemon and its class loaders.
 */
@Suppress("UNCHECKED_CAST" /* The property is written here and nowhere else. */)
private fun Project.attestationSources(): MutableList<Provider<RegularFile>> {
    val key = "io.spine.gradle.publish.attestationSources"
    val properties = extensions.extraProperties
    if (!properties.has(key)) {
        properties.set(key, mutableListOf<Provider<RegularFile>>())
    }
    return properties.get(key) as MutableList<Provider<RegularFile>>
}

/**
 * Returns the digest of every file this project publishes, keyed by the name
 * under which the file is published.
 *
 * Two artifacts of one project resolving to the same published name fail the
 * build: keeping either one silently drops the other from the attestation.
 */
private fun Project.publishedArtifacts(): Map<String, String> {
    val result = mutableMapOf<String, String>()

    fun record(name: String, file: File) {
        val replaced = result.put(name, file.sha256())
        check(replaced == null) {
            "The project `$path` publishes two artifacts as `$name`."
        }
    }

    mavenPublications().forEach { publication ->
        publication.rejectMavenSnapshot()
        val base = "${publication.artifactId}-${publication.version}"
        publication.artifacts.forEach { artifact ->
            val classifier = artifact.classifier?.takeIf { it.isNotEmpty() }
            val suffix = classifier?.let { "-$it" } ?: ""
            record("$base$suffix.${artifact.extension}", artifact.file)
        }
        pomFileOf(publication)?.let { record("$base.pom", it) }
        moduleFileOf(publication)?.let { record("$base.module", it) }
    }
    return result
}

/**
 * Returns the generated POM of the given [publication], or `null` if the
 * generating task has not written it.
 */
private fun Project.pomFileOf(publication: MavenPublication): File? =
    tasks.withType(GenerateMavenPom::class.java)
        .findByName("generatePomFileFor${publication.taskSuffix()}")
        ?.destination
        ?.takeIf { it.exists() }

/**
 * Returns the generated Gradle module metadata of the given [publication], or
 * `null` if the metadata is disabled or its task has not written it.
 */
private fun Project.moduleFileOf(publication: MavenPublication): File? =
    tasks.withType(GenerateModuleMetadata::class.java)
        .findByName("generateMetadataFileFor${publication.taskSuffix()}")
        ?.outputFile
        ?.get()
        ?.asFile
        ?.takeIf { it.exists() }

/**
 * Fails if this publication has a Maven snapshot version.
 *
 * Maven treats a version ending in `-SNAPSHOT` as mutable and deploys it under
 * a timestamped name — `spine-base-1.2.3-20260923.165835-1.jar` — assigned at
 * upload time. The names derived here would then belong to no uploaded file,
 * and the attestation would describe artifacts nobody can resolve.
 *
 * The version policy of the SDK does not produce such versions: an interim
 * version is `MAJOR.MINOR.PATCH-SNAPSHOT.NUMBER`, which does not end in
 * `-SNAPSHOT` and is published as an ordinary immutable release. This guards
 * the case anyway, because the alternative to failing is a signed statement
 * about files that were never published.
 */
private fun MavenPublication.rejectMavenSnapshot() {
    check(!version.endsWith("-SNAPSHOT")) {
        "Cannot attest `$artifactId`: Maven deploys the snapshot version" +
                " `$version` under a timestamped name that this manifest cannot" +
                " predict. Interim versions use the `-SNAPSHOT.<number>` form."
    }
}

/**
 * Returns the part of the name of a `generate...` task that identifies
 * this publication.
 */
private fun MavenPublication.taskSuffix(): String =
    "${name.replaceFirstChar { it.titlecase(Locale.ROOT) }}Publication"

/**
 * Returns the SHA-256 digest of this file as a lowercase hexadecimal string.
 *
 * The file is streamed, as published artifacts may be large.
 */
private fun File.sha256(): String {
    check(exists()) {
        "Cannot attest a missing artifact file: `$this`."
    }
    val digest = MessageDigest.getInstance("SHA-256")
    forEachBlock { buffer, bytesRead ->
        digest.update(buffer, 0, bytesRead)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
