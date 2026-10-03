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

// The steps of registering the SBOMs of the publications belong together.
@file:Suppress("TooManyFunctions")

package io.spine.gradle.publish

import DocumentationSettings
import io.spine.gradle.SpineTaskGroup
import java.io.File
import java.net.URI
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.spdx.sbom.gradle.SpdxSbomExtension
import org.spdx.sbom.gradle.SpdxSbomPlugin
import org.spdx.sbom.gradle.SpdxSbomTask
import org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension

/**
 * Publishes a Software Bill of Materials (SBOM) with each artifact of this build.
 *
 * Usage:
 * ```
 * PublicationSbom.registerTasks(project, projectsToPublish)
 * ```
 *
 * The SBOM is an SPDX 2.3 document, written by the
 * [SPDX Gradle Plugin](https://github.com/spdx/spdx-gradle-plugin) and completed by
 * [PublicationSbomTask]. It lists what an artifact depends on at runtime — never the
 * build tooling, nor the libraries used only to test it. Being published next to the
 * artifact, as `<artifactId>-<version>.spdx.json`, it enters the manifest of
 * [PublicationChecksums], so the provenance attestation covers it too. It leaves the
 * packaging the POM declares as it is.
 *
 * ## One SBOM per published artifact
 *
 * Each publication gets an SBOM of its own, named after the artifact it publishes. The
 * publications of a JVM module describe the same dependencies — its `runtimeClasspath` —
 * unless one describes its SBOM itself, as the next section tells.
 * A Kotlin Multiplatform module publishes an artifact per target, so the SBOM of each
 * target publication describes the dependencies of that target — except for an Android
 * target compiled per build variant, which gets none yet, with a warning. The
 * `kotlinMultiplatform` umbrella publication has no runtime of its own, and gets none.
 * A Kotlin/Native compilation has no runtime configuration either: its klibs are linked
 * into the binary of the consumer, so the SBOM of a Native target describes the
 * dependencies of its compilation instead.
 *
 * The Gradle Plugin Portal does not receive the SBOM: its upload skips files other than
 * JARs, so the SBOM of a Gradle plugin is published to the Maven repositories only.
 * A plugin marker publication consists of a POM alone, and gets no SBOM.
 *
 * ## Publications of files
 *
 * A publication made from a software component has the dependencies of its module, as
 * its POM does. A publication of a file, made with `artifact(...)`, publishes what its
 * module makes of it: a POM written by hand, other modules and libraries packed into
 * a JAR. So its module [describes][MavenPublication.sbom] what the SBOM lists: which
 * configuration holds the dependencies of the artifact, and what the artifact bundles.
 * The SBOM relates the artifact to each of its dependencies by `DEPENDS_ON`, and to each
 * component bundled into it by `CONTAINS`, leaving out what it neither depends on nor
 * bundles. `uber-jar-module` describes its fat JAR this way.
 *
 * ## Naming the modules of this build
 *
 * The plugin lists a module of this build under the name of its Gradle project, with
 * neither a license nor a package URL — `client` rather than `io.spine:spine-client`.
 * [PublicationSbomTask] renames it after the publication of the module. The coordinates
 * are collected once all projects are evaluated, when the publications no longer change,
 * and handed to the tasks as plain values — so that no task reads the state of another
 * project while it runs.
 */
internal object PublicationSbom {

    /**
     * The name of the task writing every SBOM a module publishes.
     *
     * The SBOM of each publication is written by a task of its own, named by [taskNameFor].
     */
    const val taskName = "publicationSbom"

    /**
     * The name of the SPDX target describing a JVM module.
     */
    private const val moduleUnit = "publication"

    /**
     * The extension of a published SBOM, as in `<artifactId>-<version>.spdx.json`.
     */
    private const val sbomExtension = "spdx.json"

    /**
     * The [platform][KotlinTarget.platform] under which a JVM module looks up the modules
     * it depends on.
     *
     * A JVM module resolves a multiplatform sibling to the artifact of its JVM target.
     */
    private val jvmPlatform = KotlinPlatformType.jvm.name

    /**
     * The organization supplying the published artifacts, as SPDX writes one.
     *
     * Matches the `Implementation-Vendor` in the manifest of each published JAR,
     * which `write-manifest.gradle.kts` sets.
     */
    private const val spineSupplier = "Organization: CodeMatters, Lda."

    /**
     * The commit recorded in the SBOM of a local build.
     *
     * GitHub Actions supplies `GITHUB_SHA` to every workflow, so a published SBOM names
     * the actual commit. A fixed value keeps the inputs of the SBOM task of a local build
     * stable, so the task stays up to date from one commit to the next.
     */
    private const val unknownCommit = "unknown"

    /**
     * Returns the name of the task writing the SBOM published with the given [publication].
     *
     * The publication of a Kotlin Multiplatform target is named after the target.
     */
    fun taskNameFor(publication: String): String =
        publication + taskName.replaceFirstChar { it.titlecase(Locale.ROOT) }

    /**
     * Registers the tasks writing the SBOMs of the given [published] projects, and adds
     * the SBOMs to their publications.
     *
     * [host] is the project in which `spinePublishing { }` was opened.
     *
     * Whether a project is a JVM or a multiplatform module is known only once it is
     * evaluated, so the registration waits until then. It is idempotent, because
     * `spinePublishing { }` may reach one project twice, as the documentation of
     * `getOrRegister` in `PublicationChecksums.kt` explains.
     */
    fun registerTasks(host: Project, published: Set<Project>) {
        published.forEach { project ->
            project.afterEvaluate { registerUnits() }
        }
        host.rootProject.publishSbomsOnceEvaluated()
    }

    /**
     * Registers the SBOMs of this project, now that its plugins are known.
     */
    private fun Project.registerUnits() {
        val kotlin = extensions.findByType(KotlinMultiplatformExtension::class.java)
        when {
            kotlin != null -> kotlin.targets
                .matching { it.platformType != KotlinPlatformType.common }
                .configureEach { this@registerUnits.registerTargetUnit(this) }

            pluginManager.hasPlugin("java") -> registerSbom(
                SbomUnit(
                    name = moduleUnit,
                    configuration = "runtimeClasspath",
                    platform = jvmPlatform,
                    publishedWith = { !it.isPluginMarker }
                )
            )

            else -> logger.info(
                "No SBOM for `$path`: it is neither a JVM nor a Kotlin Multiplatform module."
            )
        }
    }

    /**
     * Registers the SBOM of the given Kotlin Multiplatform [target] of this project.
     *
     * A target without a `main` compilation gets none, with a warning. An Android target
     * declared with `androidTarget()` is one: it compiles per build variant, and publishes
     * a publication per variant. No Spine module publishes one, and describing its variants
     * would take the Android Gradle Plugin to test, so it is left out for now.
     */
    private fun Project.registerTargetUnit(target: KotlinTarget) {
        val main = target.compilations.findByName("main")
        if (main == null) {
            logger.warn(
                "No SBOM is published with the `${target.name}` target of `$path`: it has" +
                        " no `main` compilation."
            )
            return
        }
        val targetName = target.name
        registerSbom(
            SbomUnit(
                name = targetName,
                configuration = main.runtimeDependencyConfigurationName
                    ?: main.compileDependencyConfigurationName,
                platform = target.platform,
                publishedWith = { it.name == targetName }
            )
        )
    }

    /**
     * Configures the SPDX Gradle Plugin to describe the given [unit], and records the unit
     * so that its SBOMs are [published][publishSbomsOnceEvaluated].
     */
    private fun Project.registerSbom(unit: SbomUnit) {
        pluginManager.apply(SpdxSbomPlugin::class.java)
        val spdx = extensions.getByType(SpdxSbomExtension::class.java)
        if (spdx.targets.findByName(unit.name) != null) {
            return
        }
        spdx.onlyUseLocalLicenses.set(true)
        registerSpdxTarget(name = unit.name, configurationNames = listOf(unit.configuration))
        registerLifecycleTask()
        sbomUnits().add(unit)
    }

    /**
     * Registers the SPDX target with the given [name], describing the configurations with
     * the given names, unless it is registered already, and returns the task writing
     * its document.
     *
     * The configuration of the plugin does not depend on the coordinates of the artifact,
     * which are final only after the publications are set up: [PublicationSbomTask] puts
     * them into the document instead.
     */
    private fun Project.registerSpdxTarget(
        name: String,
        configurationNames: List<String>
    ): TaskProvider<SpdxSbomTask> {
        val spdx = extensions.getByType(SpdxSbomExtension::class.java)
        if (spdx.targets.findByName(name) == null) {
            val repository = DocumentationSettings.repoUrl(this)
            val commit = providers.environmentVariable("GITHUB_SHA").orElse(unknownCommit)
            spdx.targets.create(name) {
                configurations.set(configurationNames)
                document {
                    creator.set(spineSupplier)
                    packageSupplier.set(spineSupplier)
                }
                scm {
                    uri.set(repository)
                    revision.set(commit)
                }
            }
            spdxTaskOf(name).configure {
                taskExtension.set(NoAssertionForLocalRepositories())
            }
        }
        return spdxTaskOf(name)
    }

    /**
     * Registers the task writing the SBOM of the given [publication] of the [unit], and
     * adds the SBOM to the publication.
     *
     * The SBOM is written from the document of the unit, unless the publication
     * [describes][MavenPublication.sbom] what its SBOM lists.
     *
     * The SBOM is published without a classifier, as the SPDX Maven Plugin publishes one.
     * Gradle would count such an artifact when calculating the packaging of the POM, so the
     * packaging is [pinned][pinPackaging] before the SBOM is added. The POM then declares
     * what it would without the SBOM: `jar` for a JAR rather than `pom`, and `pom` for
     * a publication without a main artifact, such as a JAR classified `all` alone, rather
     * than `spdx.json`.
     *
     * [published] holds the coordinates of the publications of this build, keyed as
     * [PublicationSbomTask.publishedCoordinates] describes.
     */
    private fun Project.publishSbom(
        publication: MavenPublication,
        unit: SbomUnit,
        published: Map<String, String>
    ) {
        val sbomTaskName = taskNameFor(publication.name)
        if (tasks.names.contains(sbomTaskName)) {
            return
        }
        val projectPath = path
        val coordinates = publication.coordinates
        val described = publication.sbomContent?.let {
            describedSbom(publication = publication, unit = unit, content = it)
        }
        val generated = described?.spdxTask ?: spdxTaskOf(unit.name)
        val output = layout.buildDirectory.file("sbom/${publication.name}.$sbomExtension")
        val sbom = tasks.register(sbomTaskName, PublicationSbomTask::class.java) {
            group = SpineTaskGroup.name
            description = "Writes the SBOM published with the `${publication.name}`" +
                    " publication of `$projectPath`"
            source.set(generated.flatMap { it.outputFile })
            modulePath.set(projectPath)
            artifactCoordinates.set(coordinates)
            platform.set(unit.platform)
            publishedCoordinates.set(published)
            described?.let { artifactComponents.set(it.components) }
            outputFile.set(output)
        }
        publication.pinPackaging()
        publication.artifact(sbom.flatMap { it.outputFile }) {
            extension = sbomExtension
        }
    }

    /**
     * Returns the SPDX document that the SBOM of the given [publication] is written from,
     * and the components of its artifact, as the publication describes them by [content].
     *
     * The document describes the configuration holding the dependencies of the artifact,
     * followed by those in which its bundled content is found. In this order, the plugin
     * relates the dependencies of the artifact as their own graph does. If these are
     * the configuration of the [unit] alone, as for a fat JAR of the runtime classpath,
     * the document of the unit serves. Otherwise, the publication gets one of its own.
     *
     * Nothing is resolved here. The components are resolved as the task writing the SBOM
     * runs, and so is each configuration, by a task of its own project. A Shadow task
     * bundling content is realized, though, to tell the configurations it packs; see
     * [sourceOf].
     */
    private fun Project.describedSbom(
        publication: MavenPublication,
        unit: SbomUnit,
        content: SbomContent
    ): DescribedSbom {
        val dependencies = content.dependenciesFrom
            ?.also { requireResolvable(it, publication) }
            ?: configurations.getByName(unit.configuration)
        val bundles = content.bundles.map { sourceOf(it, publication) }
        val configurationNames = (listOf(dependencies) + bundles.flatMap { it.configurations })
            .map { it.name }
            .distinct()
        val spdxTask = if (configurationNames == listOf(unit.configuration)) {
            spdxTaskOf(unit.name)
        } else {
            registerSpdxTarget(
                name = "${publication.name}Publication",
                configurationNames = configurationNames
            )
        }
        val own = ComponentKey.ofProject(path)
        val bundleComponents = bundles.map { it.components }
        val bundled = combined(bundleComponents, emptySet()) { all, next -> all + next }
        val components = dependencies.componentKeys().zip(bundled) { dependencyKeys, bundleKeys ->
            ArtifactComponents(dependencies = dependencyKeys - own, bundled = bundleKeys - own)
        }
        return DescribedSbom(spdxTask = spdxTask, components = components)
    }

    /**
     * Registers the task writing every SBOM this project publishes, unless it is
     * registered already.
     */
    private fun Project.registerLifecycleTask() {
        if (tasks.names.contains(taskName)) {
            return
        }
        val projectPath = path
        val sboms = tasks.withType(PublicationSbomTask::class.java)
        tasks.register(taskName) {
            group = SpineTaskGroup.name
            description = "Writes every SBOM `$projectPath` publishes"
            dependsOn(sboms)
        }
    }

    /**
     * Publishes the SBOMs of the recorded units of every project once all projects are
     * evaluated, with each publication of a unit.
     *
     * Only then are the publications final. Until then, a publication may still come or
     * go: `java-gradle-plugin` adds `pluginMaven` in an `afterEvaluate` of its own, which
     * a build may then remove. A unit records itself instead of registering a hook of its
     * own, because a multiplatform target is registered in a callback, where Gradle allows
     * no listener to be added.
     *
     * Registered once per build, on the root project. The coordinates of the publications
     * are handed to the tasks as plain values, so no task reads a provider, nor the state
     * of another project, while it runs.
     */
    private fun Project.publishSbomsOnceEvaluated() {
        val key = "io.spine.gradle.publish.sbomsPublishedOnceEvaluated"
        val properties = extensions.extraProperties
        if (properties.has(key)) {
            return
        }
        properties.set(key, true)
        val root = this
        gradle.projectsEvaluated {
            val published = root.collectPublishedCoordinates()
            root.allprojects.forEach { module ->
                val publications = module.mavenPublications()
                val units = module.sbomUnits()
                units.forEach { unit ->
                    publications.filter(unit.publishedWith).forEach {
                        module.publishSbom(publication = it, unit = unit, published = published)
                    }
                }
                module.warnOfUnpublishedSbomContent(publications, units)
            }
        }
    }
}

/**
 * Warns of each of the given [publications] that [describes][MavenPublication.sbom] its
 * SBOM, but gets no SBOM published with it by any of the [units] of this project.
 *
 * Such is a plugin marker, the umbrella publication of a Kotlin Multiplatform module,
 * or a publication of a module that `spinePublishing` does not publish.
 */
private fun Project.warnOfUnpublishedSbomContent(
    publications: Collection<MavenPublication>,
    units: List<SbomUnit>
) {
    publications
        .filter { it.sbomContent != null }
        .filterNot { publication -> units.any { it.publishedWith(publication) } }
        .forEach {
            logger.warn(
                "The `${it.name}` publication of `$path` describes its SBOM," +
                        " but no SBOM is published with it."
            )
        }
}

/**
 * The SPDX document the SBOM of a publication is written from, and the components of
 * the artifact, as the publication [describes][MavenPublication.sbom] them.
 */
private class DescribedSbom(

    /** The task writing the document. */
    val spdxTask: TaskProvider<SpdxSbomTask>,

    /** The components the artifact depends on, and those bundled into it. */
    val components: Provider<ArtifactComponents>
)

/**
 * The configurations in which a part of the content of an artifact is found,
 * and the components of them bundled into the artifact.
 */
private class BundleSource(

    /** The configurations holding the content. */
    val configurations: List<Configuration>,

    /** The components bundled into the artifact, by their keys. */
    val components: Provider<Set<ComponentKey>>
)

/**
 * Returns the configurations in which the given [bundle] of the content of the
 * [publication] is found, and the components of them bundled into the artifact.
 *
 * A Shadow task bundles the components whose files its dependency filter leaves
 * among its included dependencies.
 *
 * The Shadow task is realized, rather than its values being mapped from its provider.
 * A value derived from the provider of a task may carry the task as its producer, which
 * would make the task writing the SBOM build the fat JAR first. Realizing the task only
 * runs its configuration.
 */
private fun Project.sourceOf(bundle: Bundle, publication: MavenPublication): BundleSource =
    when (bundle) {
        is Bundle.Components -> {
            val configuration = bundle.configuration
            requireResolvable(configuration, publication)
            BundleSource(listOf(configuration), configuration.componentKeys())
        }

        is Bundle.Shadowed -> {
            val task = bundle.task.get()
            require(task.project.path == path) {
                "${sbomOf(publication)} describes what `${task.path}` bundles," +
                        " a task of another project."
            }
            val configurations = task.configurations.get().toList()
            configurations.forEach { requireResolvable(it, publication) }
            val componentsByFiles = configurations.map { it.componentsByFile() }
            val byFile = combined(componentsByFiles, emptyMap()) { all, next -> all + next }
            val components = task.includedDependencies.elements.zip(byFile) { files, keys ->
                files.mapNotNull { keys[it.asFile] }.toSet()
            }
            BundleSource(configurations, components)
        }
    }

/**
 * Ensures that the given [configuration], which the [publication] names in the
 * description of its SBOM, belongs to this project and can be resolved.
 *
 * The SPDX Gradle Plugin looks a configuration up by its name in the project of
 * the SBOM, and resolves it.
 */
private fun Project.requireResolvable(
    configuration: Configuration,
    publication: MavenPublication
) {
    val subject = sbomOf(publication)
    require(configurations.findByName(configuration.name) === configuration) {
        "$subject names `${configuration.name}`, a configuration of another project."
    }
    require(configuration.isCanBeResolved) {
        "$subject names `${configuration.name}`, which cannot be resolved."
    }
}

/**
 * Returns how a message names the SBOM of the given [publication] of this project.
 */
private fun Project.sbomOf(publication: MavenPublication): String =
    "The SBOM of the `${publication.name}` publication of `$path`"

/**
 * Returns the keys of the components in the dependency graph of this configuration,
 * which it resolves as the value of the provider is obtained.
 */
private fun Configuration.componentKeys(): Provider<Set<ComponentKey>> =
    incoming.resolutionResult.rootComponent.map(::componentKeysFrom)

/**
 * Returns the keys of the components in the dependency graph of the given [root].
 */
private fun componentKeysFrom(root: ResolvedComponentResult): Set<ComponentKey> {
    val visited = mutableSetOf<ComponentIdentifier>()
    val queue = ArrayDeque(listOf(root))
    return buildSet {
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (visited.add(component.id)) {
                ComponentKey.of(component.id)?.let(::add)
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .forEach { queue.add(it.selected) }
            }
        }
    }
}

/**
 * Returns the keys of the components whose files this configuration resolves,
 * by the files.
 */
private fun Configuration.componentsByFile(): Provider<Map<File, ComponentKey>> =
    incoming.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts
            .mapNotNull { artifact ->
                ComponentKey.of(artifact.id.componentIdentifier)?.let { artifact.file to it }
            }
            .toMap()
    }

/**
 * Returns a provider of the given [values] combined with the [combine] function,
 * starting with the [empty] value.
 */
private fun <T : Any> Project.combined(
    values: List<Provider<T>>,
    empty: T,
    combine: (T, T) -> T
): Provider<T> =
    values.fold(providers.provider { empty }) { all, next -> all.zip(next, combine) }

/**
 * The coordinates of this publication, as `group:artifactId:version`.
 */
private val MavenPublication.coordinates: String
    get() = "$groupId:$artifactId:$version"

/**
 * Stores the packaging Gradle calculates for the POM of this publication as an explicit
 * value, so that artifacts added later do not change it.
 *
 * Until it is set explicitly, the packaging has no value of its own: Gradle calculates it
 * from the artifacts of the publication whenever it is read, taking the extension of
 * the only artifact without a classifier, or `pom` for none or several. Adding an artifact
 * without a classifier therefore changes the packaging, unless an explicit value is set.
 * A packaging the build has set explicitly is read back unchanged, and so it is kept.
 *
 * The packaging is calculated from the artifacts the publication has when it is read.
 * For a publication made from a software component, the first read also takes
 * the artifacts of the component into the publication for good. So the publication
 * must be final by the time this function is called, as it is once all projects are evaluated.
 */
private fun MavenPublication.pinPackaging() {
    // The explicit type makes this fail to compile, rather than set the packaging
    // to itself, once Gradle turns `MavenPom.getPackaging()` into a lazy property.
    val calculated: String = pom.packaging
    pom.packaging = calculated
}

/**
 * Returns the coordinates of the Maven publications of this build that a module can
 * depend on, keyed as [PublicationSbomTask.publishedCoordinates] describes.
 */
private fun Project.collectPublishedCoordinates(): Map<String, String> = buildMap {
    allprojects.forEach { module ->
        val publications = module.mavenPublications().filterNot { it.isPluginMarker }
        publications.singleOrNull()?.let { put(module.path, it.coordinates) }
        module.targetPublications(publications).forEach { (platform, publication) ->
            put(publicationKey(module.path, platform), publication.coordinates)
        }
    }
}

/**
 * Returns the publications of the Kotlin Multiplatform targets of this project among the
 * given ones, keyed by the [platform][KotlinTarget.platform] of each target.
 *
 * A platform with several targets is left out: which of them a dependency resolves to
 * depends on attributes that the platform does not capture.
 */
private fun Project.targetPublications(
    publications: Collection<MavenPublication>
): Map<String, MavenPublication> {
    val kotlin = extensions.findByType(KotlinMultiplatformExtension::class.java)
        ?: return emptyMap()
    val byName = publications.associateBy { it.name }
    return kotlin.targets
        .filter { it.platformType != KotlinPlatformType.common }
        .groupBy { it.platform }
        .mapNotNull { (platform, targets) ->
            targets.singleOrNull()?.let { byName[it.name] }?.let { platform to it }
        }
        .toMap()
}

/**
 * The platform this target compiles for: the name of its platform type, such as `jvm`,
 * followed by the Kotlin/Native target of a native one, as in `native:macos_arm64`.
 *
 * Gradle resolves a dependency on a multiplatform module to the artifact of its target
 * for the platform of the consumer, however either target is named.
 */
private val KotlinTarget.platform: String
    get() {
        val native = attributes.getAttribute(KotlinNativeTarget.konanTargetAttribute)
        return if (native == null) platformType.name else "${platformType.name}:$native"
    }

/**
 * Returns the task the SPDX Gradle Plugin registers for the target with the given [name].
 */
private fun Project.spdxTaskOf(name: String): TaskProvider<SpdxSbomTask> =
    tasks.named(
        "spdxSbomFor${name.replaceFirstChar { it.titlecase(Locale.ROOT) }}",
        SpdxSbomTask::class.java
    )

/**
 * Returns the units whose SBOMs this project publishes, as recorded so far.
 *
 * The list is attached to the project, so that it lives exactly as long as the build does.
 * Holding it in [PublicationSbom] would share it between builds, which reuse a Gradle
 * daemon and its class loaders.
 */
@Suppress("UNCHECKED_CAST" /* The property is written here and nowhere else. */)
private fun Project.sbomUnits(): MutableList<SbomUnit> {
    val key = "io.spine.gradle.publish.sbomUnits"
    val properties = extensions.extraProperties
    if (!properties.has(key)) {
        properties.set(key, mutableListOf<SbomUnit>())
    }
    return properties.get(key) as MutableList<SbomUnit>
}

/**
 * The dependencies described by one SPDX target, and the publications whose SBOMs
 * are written from that description.
 */
private class SbomUnit(

    /** Names the SPDX target. */
    val name: String,

    /** The configuration holding the dependencies of the artifacts. */
    val configuration: String,

    /**
     * The [platform][KotlinTarget.platform] under which the modules of this build
     * are looked up.
     */
    val platform: String,

    /** Tells whether an SBOM of this unit is published with the given publication. */
    val publishedWith: (MavenPublication) -> Boolean
)

private val noAssertion = URI.create("NOASSERTION")

/**
 * Leaves the download location of a dependency resolved from a local repository
 * unasserted, keeping its package URL.
 *
 * The SPDX Gradle Plugin copies the URL of the repository a dependency comes from into
 * the download location of its package. SPDX 2 accepts no `file:` URL there, and since
 * version 0.12.0 the plugin fails the build on one — see
 * [spdx-gradle-plugin#192](https://github.com/spdx/spdx-gradle-plugin/issues/192).
 * Spine builds resolve modules from Maven Local, so any build using a locally published
 * one would fail. A local path means nothing to the reader of a published SBOM anyway.
 */
private class NoAssertionForLocalRepositories : DefaultSpdxSbomTaskExtension() {

    override fun mapRepoUri(original: URI?, moduleId: ModuleVersionIdentifier): URI? =
        if (original?.scheme == "file") noAssertion else original
}
