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

@file:Suppress("TooManyFunctions")

package io.spine.gradle.publish

import io.spine.gradle.repo.Repository
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

/**
 * Configures [SpinePublishing] extension.
 *
 * This extension sets up publishing of artifacts to Maven repositories.
 *
 * The extension can be configured for single- and multi-module projects.
 *
 * ## Using in a multi-module project
 *
 * When used with a multi-module project, the extension should be opened in a root project's
 * build file. The published modules are specified explicitly by their names:
 *
 * ```kotlin
 * spinePublishing {
 *     modules = setOf(
 *         "subprojectA",
 *         "subprojectB",
 *     )
 *     destinations = PublishingRepos.run { setOf(
 *         cloudArtifactRegistry,
 *         gitHub("<ProjectRepo>")  // The name of the GitHub repository of the project.
 *     )}
 * }
 * ```
 *
 * ### Filtering out test-only modules
 *
 * Sometimes a functional or an integration test requires a significant amount of
 * configuration code which is better understood when isolated into a separate module.
 * Conventionally, we use the `-tests` suffix for naming such modules.
 *
 * In order to avoid publishing of such a test-only module, we use the following extensions
 * for the Gradle [Project] class: [productionModules], [productionModuleNames].
 * So the above code for specifying the modules to publish could be rewritten as follows:
 *
 * ```kotlin
 * spinePublishing {
 *     modules = productionModuleNames.toSet()
 * }
 * ```
 * This code works for most of the projects.
 *
 * ### Arranging custom publishing for a module
 * ```kotlin
 *
 * 1. Modify the list of standardly published modules in the root project like this:
 *
 * ```kotlin
 * spinePublishing {
 *     modules = productionModuleNames
 *       .minus("my-custom-module")
 *       .toSet()
 *
 *     modulesWithCustomPublishing = setOf(
 *         "my-custom-module"
 *     )
 *
 *     // ...
 * }
 * ```
 * 2. Arrange the custom publishing in the `my-custom-module` project.
 *
 * ## Using in a single-module project
 *
 * When used with a single-module project, the extension should be opened in a project's build file.
 * Only destinations should be specified:
 *
 * ```kotlin
 * spinePublishing {
 *     destinations = PublishingRepos.run { setOf(
 *         cloudArtifactRegistry,
 *         gitHub("<ProjectRepo>")
 *     )}
 * }
 * ```
 *
 * ## Publishing modules
 *
 * It is worth mentioning that publishing of a module can be configured only from a single place.
 * For example, declaring `subprojectA` as published in a root project and opening
 * `spinePublishing` extension within `subprojectA` itself would lead to an exception.
 *
 * In Gradle, in order to publish something somewhere, one should create a publication. In each
 * of published modules, the extension will create a [publication][StandardJavaPublicationHandler]
 * named "mavenJava". All artifacts published by this extension belong to this publication.
 *
 * ## Published artifacts
 *
 * By default, along with the compilation output of the `main` source set, the extension publishes
 * the following artifacts:
 *
 * 1. [sourcesJar] — sources from the `main` source set. Includes handcrafted and generated
 *    code in Java, Kotlin, and `.proto` files.
 *
 * 2. [protoJar] – only `.proto` sources from the `main` source set. It is published only if
 *   Proto files are actually present in the source set.
 *
 * 3. [javadocJar] — Javadoc, generated upon Java sources from the `main` source set.
 *   If Javadoc for Kotlin is also needed, apply the Dokka plugin.
 *   It tunes the `javadoc` task to generate docs upon Kotlin sources as well.
 *
 * 4. [htmlDocsJar] — documentation generated by Dokka for Kotlin and Java sources
 *   using the Kotlin API mode.
 *
 * Additionally, [testJar] artifact can be published. This artifact contains compilation output
 * of the `test` source set. Use [SpinePublishing.testJar] to enable its publishing.
 *
 * @see [artifacts]
 * @see SpinePublishing
 */
fun Project.spinePublishing(block: SpinePublishing.() -> Unit) {
    apply<MavenPublishPlugin>()
    val name = SpinePublishing::class.java.simpleName
    val extension = with(extensions) {
        findByType<SpinePublishing>() ?: create(name, project)
    }
    extension.run {
        block()
        configured()
    }
}

/**
 * A Gradle extension for setting up publishing of modules of Spine SDK modules
 * using `maven-publish` plugin.
 *
 * ### Implementation Note
 *
 * This extension is overloaded with responsibilities.
 * It basically does what an extension AND a Gradle plugin would normally do.
 *
 * We [should introduce a plugin class](https://github.com/SpineEventEngine/config/issues/562)
 * and move the code related to creating tasks or setting dependencies between them into the plugin.
 *
 * @param project The project in which the extension is opened. By default, this project will be
 *   published as long as a [set][modules] of modules to publish is not specified explicitly.
 *
 * @see spinePublishing
 */
open class SpinePublishing(private val project: Project) {

    companion object {

        /**
         * The default prefix added before a module name when publishing artifacts.
         */
        const val DEFAULT_PREFIX = "spine-"
    }

    private val testJar = TestJar()

    /**
     * Set of modules to be published.
     *
     * Both the module's name or path can be used.
     *
     * Use this property if the extension is configured from a root project's build file.
     *
     * If left empty, the [project], in which the extension is opened, will be published.
     *
     * Empty by default.
     */
    var modules: Set<String> = emptySet()

    /**
     * Controls whether the [module][project] needs standard publications.
     *
     * Default value is `false`.
     *
     * In a single module [project], settings this property to `true` it tells
     * that the project configures the publication in a specific way and
     * [CustomPublicationHandler] should be used.
     * Otherwise, the extension will configure the
     * [standard publication][StandardJavaPublicationHandler].
     *
     * This property is an analogue of [modulesWithCustomPublishing] in
     * [multi-module][Project.getSubprojects] projects,
     * for which [spinePublishing] is configured individually.
     *
     * Setting of this property to `true` and having a non-empty [modules] property
     * in the project to which the extension is applied will lead to [IllegalStateException].
     *
     * Settings this property to `true` in a subproject serves only the documentation purposes.
     * This subproject still must be listed in the [modulesWithCustomPublishing] property in
     * the extension of the [rootProject][Project.getRootProject], so that its publication
     * can be configured in a specific way.
     */
    var customPublishing = false

    /**
     * Set of modules that have custom publications and do not need standard ones.
     *
     * Empty by default.
     */
    var modulesWithCustomPublishing: Set<String> = emptySet()

    /**
     * Set of repositories, to which the resulting artifacts will be sent.
     *
     * Usually, Spine-related projects are published to one or more repositories,
     * declared in [PublishingRepos]:
     *
     * ```kotlin
     * destinations = PublishingRepos.run { setOf(
     *      cloudArtifactRegistry,
     *      gitHub("<ProjectRepo>") // The name of the GitHub repository of the project.
     * )}
     * ```
     *
     * If the property is not initialized, the destinations will be taken from
     * the parent project.
     */
    lateinit var destinations: Set<Repository>

    /**
     * A prefix to be added before the name of each artifact.
     */
    var artifactPrefix: String = DEFAULT_PREFIX

    /**
     * Allows enabling publishing of [testJar] artifact, containing compilation output
     * of "test" source set.
     *
     * Here's an example of how to enable it for some of the published modules:
     *
     * ```
     * spinePublishing {
     *     modules = setOf(
     *         "subprojectA",
     *         "subprojectB",
     *     )
     *     testJar {
     *         inclusions = setOf(
     *             "subprojectB",
     *         )
     *     }
     * }
     * ```
     *
     * For all modules, or when the extension is configured within a published module itself:
     *
     * ```
     * spinePublishing {
     *     testJar {
     *         enabled = true
     *     }
     * }
     * ```
     *
     * The resulting artifact is available under the "test" classifier.
     * For example, in Gradle 7+, one could depend on it like this:
     *
     * ```
     * implementation("io.spine:spine-client:$version@test")
     * ```
     */
    fun testJar(block: TestJar.() -> Unit)  = testJar.run(block)

    /**
     * Called to notify the extension that its configuration is completed.
     *
     * On this stage the extension will validate the received configuration and set up
     * `maven-publish` plugin for each published module.
     */
    internal fun configured() {
        ensureTestJarInclusionsArePublished()
        ensureModulesNotDuplicated()
        ensureCustomPublishingNotMisused()

        val projectsToPublish = projectsToPublish()
        projectsToPublish.forEach { project ->
            val jarFlags = JarFlags.create(project.name, testJar)
            project.setUpPublishing(jarFlags)
        }
    }

    /**
     * Maps the names of published modules to [Project] instances.
     *
     * The method considers two options:
     *
     * 1. The [set][modules] of subprojects to publish is not empty. It means that the extension
     *   is opened from a root project.
     * 2. The [set][modules] is empty. Then, the [project] in which the extension is opened
     *   will be published.
     *
     * @see modules
     */
    private fun projectsToPublish(): Collection<Project> {
        if (project.subprojects.isEmpty()) {
            return setOf(project)
        }
        return modules.union(modulesWithCustomPublishing)
            .map { name -> project.project(name) }
            .ifEmpty { setOf(project) }
    }

    /**
     * Sets up `maven-publish` plugin for the given project.
     *
     * Firstly, an instance of [PublicationHandler] is created for the project depending
     * on the nature of the publication process configured.
     * Then, this the handler is scheduled to apply on [Project.afterEvaluate].
     *
     * General rule of thumb is to avoid using [Project.afterEvaluate] of this closure,
     * as it configures a project when its configuration is considered completed.
     * Which is quite counter-intuitive.
     *
     * We selected to use [Project.afterEvaluate] so that we can configure publishing of multiple
     * modules from a root project. When we do this, we configure publishing for a module,
     * a build file of which has not been even evaluated yet.
     *
     * The simplest example here is specifying of `version` and `group` for Maven coordinates.
     * Let's suppose they are declared in a module's build file. It is a common practice.
     * But publishing of the module is configured from a root project's build file.
     * By the time when we need to specify them, we just don't know them.
     * As the result, we have to use [Project.afterEvaluate] in order to guarantee that
     * the module will be configured by the time we configure publishing for it.
     */
    private fun Project.setUpPublishing(jarFlags: JarFlags) {
        val customPublishing = modulesWithCustomPublishing.contains(name) || customPublishing
        val destinations = project.publishTo()
        val handler = if (customPublishing) {
            CustomPublicationHandler.serving(project, destinations)
        } else {
            StandardJavaPublicationHandler.serving(project, destinations, jarFlags)
        }
        afterEvaluate {
            handler.apply()
        }
    }

    /**
     * Obtains the set of repositories for publishing.
     *
     * If there is a local instance of [io.spine.gradle.publish.SpinePublishing] extension,
     * the [destinations] are obtained from this instance.
     * Otherwise, the function attempts to obtain it from a [parent project][Project.getParent].
     * If there is no a parent project, an empty set is returned.
     *
     * The normal execution should end up at the root project of a multi-module project
     * if there are no custom destinations specified by the local extension.
     */
    private fun Project.publishTo(): Set<Repository> {
        val ext = localSpinePublishing
        if (ext != null && ext::destinations.isInitialized) {
            return destinations
        }
        return parent?.publishTo() ?: emptySet()
    }

    /**
     * Obtains an artifact ID for the given project.
     *
     * It consists of a project's name and [prefix][artifactPrefix]:
     * `<artifactPrefix><project.name>`.
     */
    fun artifactId(project: Project): String = "$artifactPrefix${project.name}"

    /**
     * Ensures that all modules, marked as included into [testJar] publishing,
     * are actually published.
     *
     * It makes no sense to tell a module publish [testJar] artifact, if the module is not
     * published at all.
     */
    private fun ensureTestJarInclusionsArePublished() {
        val nonPublishedInclusions = testJar.inclusions.minus(modules)
        if (nonPublishedInclusions.isNotEmpty()) {
            error(
                "One or more modules are marked as `included into test JAR publication`," +
                        " but they are not even published: $nonPublishedInclusions."
            )
        }
    }

    /**
     * Ensures that publishing of a module is configured only from a single place.
     *
     * We allow configuration of publishing from two places - a root project and the module itself.
     * Here we verify that publishing of a module is not configured in both places simultaneously.
     */
    private fun ensureModulesNotDuplicated() {
        val rootProject = project.rootProject
        if (rootProject == project) {
            return
        }

        val rootExtension = with(rootProject.extensions) { findByType<SpinePublishing>() }
        rootExtension?.let { rootPublishing ->
            val thisProject = setOf(project.name, project.path)
            if (thisProject.minus(rootPublishing.modules).size != 2) {
                error(
                    "Publishing of `$thisProject` module is already configured in a root project!"
                )
            }
        }
    }

    private fun ensureCustomPublishingNotMisused() {
        if (modules.isNotEmpty() && customPublishing) {
            error("`customPublishing` property can be set only if `spinePublishing` extension " +
                    "is open in an individual module, so `modules` property should be empty.")
        }
    }
}
