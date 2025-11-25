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

package io.spine.gradle.publish

import LicenseSettings
import io.spine.gradle.isSnapshot
import io.spine.gradle.repo.Repository
import io.spine.gradle.report.pom.InceptionYear
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.invocation.BuildInvocationDetails
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.support.serviceOf

/**
 * The name of the Maven Publishing Gradle plugin.
 */
private const val MAVEN_PUBLISH = "maven-publish"

/**
 * Abstract base for handlers of publications in a project
 * with [spinePublishing] settings declared.
 *
 * @param project The project to which the handler is applied.
 * @param destinations The repositories for publishing artifacts of this project.
 *   In a multi-module project the destinations can be re-defined by
 *   specifying custom values in
 *   the [`spinePublishing`][io.spine.gradle.publish.SpinePublishing.destinations]
 *   extension applied to the subproject.
 */
internal sealed class PublicationHandler(
    protected val project: Project,
    protected var destinations: Set<Repository>
) {
    /**
     * Remembers if the [apply] function was called by this handler.
     */
    private var applied: Boolean = false

    /**
     * Overwrites the [destinations] property with the given set.
     */
    fun publishTo(alternativeDestinations: Set<Repository>) {
        if (alternativeDestinations.isEmpty()) {
            project.logger.info(
                "The project ${project.path} is not going to be published because" +
                        " the publication handler `${this@PublicationHandler}`" +
                        " got an empty set of new `destinations`."
            )
        }
        destinations = alternativeDestinations
    }

    /**
     * Configures the publication of the associated [project].
     */
    fun apply() {
        synchronized(project) {
            if (applied) {
                return
            }
            project.run {
                // We apply the `maven-publish` plugin for modules with standard
                // publishing automatically because they don't need custom DSL
                // in their `build.gradle.kts` files.
                // All the job is done by the `SpinePublishing` extension and
                // `StandardPublicationHandler` instance associated with this project.
                if (!hasCustomPublishing) {
                    apply(plugin = MAVEN_PUBLISH)
                }
                // And we do not apply the plugin for modules with custom publishing
                // because they will need the `maven-publish` DSL to tune the publishing.
                // Therefore, we only arrange the execution of our code when the plugin
                // is applied.
                pluginManager.withPlugin(MAVEN_PUBLISH) {
                    handlePublications()
                    registerDestinations()
                    configurePublishTask(destinations)
                    applied = true
                }
            }
        }
    }

    /**
     * Either handles publications already declared in the associated [project]
     * or creates new ones.
     */
    abstract fun handlePublications()

    /**
     * Goes through the [destinations] and registers each as a repository for publishing
     * in the given Gradle project.
     */
    private fun registerDestinations() {
        val repositories = project.publishingExtension.repositories
        destinations.forEach { destination ->
            repositories.register(project, destination)
        }
    }

    /**
     * Copies the attributes of Gradle [Project] to this [MavenPublication].
     *
     * The following project attributes are copied:
     *  * [group][Project.getGroup];
     *  * [version][Project.getVersion];
     *  * [description][Project.getDescription].
     *
     * Also, this function adds the [artifactPrefix][SpinePublishing.artifactPrefix] to
     * the [artifactId][MavenPublication.setArtifactId] of this publication,
     * if the prefix is not added yet.
     *
     * Finally, the Apache Software License 2.0 is set as the only license
     * under which the published artifact is distributed.
     */
    protected fun MavenPublication.copyProjectAttributes() {
        groupId = project.group.toString()
        val prefix = project.spinePublishing.artifactPrefix
        if (!artifactId.startsWith(prefix)) {
            artifactId = prefix + artifactId
        }
        version = project.version.toString()
        pom.description.set(project.description)
        pom.inceptionYear.set(InceptionYear.value)
        pom.licenses {
            license {
                name.set(LicenseSettings.name)
                url.set(LicenseSettings.url)
                distribution.set(LicenseSettings.url)
            }
        }
        pom.scm {
            DocumentationSettings.run {
                url.set(repoUrl(project))
                connection.set(connectionUrl(project))
                developerConnection.set(developerConnectionUrl(project))
            }
        }
    }

    /**
     * The abstract base for factories producing instances of classes
     * derived from [io.spine.gradle.publish.PublicationHandler].
     *
     * The factory maintains associations between a path of the project to
     * its publication handler.
     *
     * If the handler already exists, its settings are updated when
     * the [serving] factory method is called.
     *
     * Otherwise, a new handler is created and associated with the project.
     *
     * @param H The type of the publication handlers produced by this repository.
     * @see serving
     */
    abstract class HandlerFactory<H : PublicationHandler> {

        /**
         * Maps a project path suffixed with build start time to the associated publication handler.
         *
         * The suffix after the project path is needed to create a new handler
         * for each build. We do not use Guava or other cache expecting the small amount
         * of memory consumption of each publication handler.
         */
        private val handlers = mutableMapOf<String, H>()

        /**
         * Computes the key for a publication handler taking the [project] and
         * its build start time.
         */
        private fun createKey(project: Project): String {
            val buildService = project.gradle.serviceOf<BuildInvocationDetails>()
            val buildStartedMillis = buildService.buildStartedTime
            val localTime = java.time.Instant.ofEpochMilli(buildStartedMillis)
            val key = "${project.path}-at-$localTime"
            return key
        }

        /**
         * Obtains an instance of [PublicationHandler] for the given project.
         *
         * If the handler for the given [project] was already created, the handler
         * gets new [destinations], [overwriting][publishTo] previously specified.
         *
         * @return the handler for the given project which would handle publishing to
         *  the specified [destinations].
         */
        fun serving(project: Project, destinations: Set<Repository>, vararg params: Any): H {
            synchronized(handlers) {
                val key = createKey(project)
                var handler = handlers[key]
                if (handler == null) {
                    handler = create(project, destinations, *params)
                    handlers[key] = handler
                } else {
                    handler.publishTo(destinations)
                }
                return handler
            }
        }

        /**
         * Creates a new publication handler for the given project.
         *
         * @param project The project to which the handler applies.
         * @param destinations The repositories for publishing artifacts of this project.
         * @param params Optional parameters to be passed as constructor parameters for
         *  classes of the type [H].
         */
        protected abstract fun create(
            project: Project,
            destinations: Set<Repository>,
            vararg params: Any
        ): H
    }
}

/**
 * Adds a Maven repository to the project specifying credentials, if they are
 * [available][Repository.credentials] from the root project.
 */
private fun RepositoryHandler.register(project: Project, repository: Repository) {
    val isSnapshot = project.version.toString().isSnapshot()
    val credentials = repository.credentials(project.rootProject)
    maven {
        name = repository.name(isSnapshot)
        url = project.uri(repository.target(isSnapshot))
        credentials {
            username = credentials?.username
            password = credentials?.password
        }
    }
}
