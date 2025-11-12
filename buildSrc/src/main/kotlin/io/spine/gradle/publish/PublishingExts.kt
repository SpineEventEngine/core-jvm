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

import dokkaKotlinJar
import io.spine.gradle.isSnapshot
import io.spine.gradle.repo.Repository
import io.spine.gradle.sourceSets
import java.util.*
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Obtains [PublishingExtension] of this project.
 */
internal val Project.publishingExtension: PublishingExtension
    get() = extensions.getByType()

/**
 * Obtains [PublicationContainer] of this project.
 */
internal val Project.publications: PublicationContainer
    get() = publishingExtension.publications

/**
 * Obtains an instance, if available, of [SpinePublishing] extension
 * applied to this project.
 */
internal val Project.localSpinePublishing: SpinePublishing?
    get() = extensions.findByType<SpinePublishing>()

/**
 * Obtains [SpinePublishing] extension from this [Project].
 *
 * If this [Project] doesn't have one, it returns [SpinePublishing]
 * declared in the root project.
 */
internal val Project.spinePublishing: SpinePublishing
    get() {
        val local = localSpinePublishing
        if (local != null) {
            return local
        }
        val fromRoot = this.rootProject.extensions.findByType<SpinePublishing>()
        if (fromRoot != null) {
            return fromRoot
        }
        error("`SpinePublishing` is not found in `${project.name}`.")
    }

/**
 * Tells if this project has custom publishing.
 *
 * For a multi-module project this is checked by presence of this project
 * in the list of [SpinePublishing.modulesWithCustomPublishing] of the root project.
 *
 * In a single-module project, the value of the [SpinePublishing.customPublishing]
 * property is returned.
 */
internal val Project.hasCustomPublishing: Boolean
    get() = rootProject.spinePublishing.modulesWithCustomPublishing.contains(name)
            || spinePublishing.customPublishing

private const val PUBLISH_TASK = "publish"

/**
 * Locates `publish` task in this [TaskContainer].
 *
 * This task publishes all defined publications to all defined repositories. To achieve that,
 * the task depends on all `publish`*PubName*`PublicationTo`*RepoName*`Repository` tasks.
 *
 * Please note, task execution would not copy publications to the local Maven cache.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:tasks">
 *     Tasks | The Maven Publish Plugin</a>
 */
internal val TaskContainer.publish: TaskProvider<Task>
    get() = named(PUBLISH_TASK)

/**
 * Sets dependencies for `publish` task in this [Project].
 *
 * This method performs the following:
 *
 *  1. When this [Project] is not a root, makes `publish` task in a root project
 *     depend on a local `publish`.
 *  2. Makes local `publish` task verify that credentials are present for each
 *     of destination repositories.
 */
internal fun Project.configurePublishTask(destinations: Set<Repository>) {
    attachCredentialsVerification(destinations)
    bindToRootPublish()
}

private fun Project.attachCredentialsVerification(destinations: Set<Repository>) {
    val checkCredentials = tasks.registerCheckCredentialsTask(destinations)
    val localPublish = tasks.publish
    localPublish.configure { dependsOn(checkCredentials) }
}

private fun Project.bindToRootPublish() {
    if (project == rootProject) {
        return
    }

    val localPublish = tasks.publish
    val rootPublish = rootProject.tasks.getOrCreatePublishTask()
    rootPublish.configure { dependsOn(localPublish) }
}

/**
 * Use this task accessor when it is not guaranteed that the task is present
 * in this [TaskContainer].
 */
private fun TaskContainer.getOrCreatePublishTask(): TaskProvider<Task> =
    if (names.contains(PUBLISH_TASK)) {
        named(PUBLISH_TASK)
    } else {
        register(PUBLISH_TASK)
    }

@Suppress(
    /* Several types of exceptions may be thrown,
       and Kotlin does not have a multi-catch support yet. */
    "TooGenericExceptionCaught"
)
private fun TaskContainer.registerCheckCredentialsTask(
    destinations: Set<Repository>,
): TaskProvider<Task> {
    val checkCredentials = "checkCredentials"
    try {
        // The result of this call is ignored intentionally.
        //
        // We expect this line to fail with the exception
        // in case the task with this name is NOT registered.
        //
        // Otherwise, we need to replace the existing task
        // to avoid checking the credentials
        // for some previously asked `destinations`.
        named(checkCredentials)
        val toConfigure = replace(checkCredentials)
        toConfigure.doLastCredentialsCheck(destinations)
        return named(checkCredentials)
    } catch (_: Exception) {
        return register(checkCredentials) { doLastCredentialsCheck(destinations) }
    }
}

private fun Task.doLastCredentialsCheck(destinations: Set<Repository>) {
    doLast {
        if (logger.isDebugEnabled) {
            val isSnapshot = project.version.toString().isSnapshot()
            val destinationsStr = destinations.joinToString(", ") { it.target(isSnapshot) }
            logger.debug(
                "Project '${project.name}': checking the credentials for repos: $destinationsStr."
            )
        }
        destinations.forEach { it.ensureCredentials(project) }
    }
}

private fun Repository.ensureCredentials(project: Project) {
    val credentials = credentials(project)
    if (Objects.isNull(credentials)) {
        throw InvalidUserDataException(
            "No valid credentials for repository `${this}`. Please make sure " +
                    "to pass username/password or a valid `.properties` file."
        )
    }
}

/**
 * Excludes Google `.proto` sources from all artifacts.
 *
 * Goes through all registered `Jar` tasks and filters out Google's files.
 */
@Suppress("unused")
fun TaskContainer.excludeGoogleProtoFromArtifacts() {
    withType<Jar>().configureEach {
        exclude { it.isGoogleProtoSource() }
    }
}

/**
 * Locates or creates `sourcesJar` task in this [Project].
 *
 * The output of this task is a `jar` archive. The archive contains sources from `main` source set.
 * The task makes sure that sources from the directories below will be included
 * in the resulting archive:
 *
 *  - Kotlin
 *  - Java
 *  - Proto
 *
 * Java and Kotlin sources are default to `main` source set since it is created by `java` plugin.
 * For Proto sources to be included – [special treatment][protoSources] is needed.
 */
internal fun Project.sourcesJar(): TaskProvider<Jar> = tasks.getOrCreate("sourcesJar") {
    dependOnGenerateProto()
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource) // Puts Java and Kotlin sources.
    from(protoSources()) // Puts Proto sources.
    exclude("desc.ref", "*.desc") // Exclude descriptor files and the descriptor reference.
}

/**
 * Locates or creates `protoJar` task in this [Project].
 *
 * The output of this task is a `jar` archive. The archive contains only
 * [Proto sources][protoSources] from `main` source set.
 */
internal fun Project.protoJar(): TaskProvider<Jar> = tasks.getOrCreate("protoJar") {
    dependOnGenerateProto()
    archiveClassifier.set("proto")
    from(protoSources())
}

/**
 * Locates or creates `testJar` task in this [Project].
 *
 * The output of this task is a `jar` archive. The archive contains compilation output
 * of `test` source set.
 */
internal fun Project.testJar(): TaskProvider<Jar> = tasks.getOrCreate("testJar") {
    archiveClassifier.set("test")
    from(sourceSets["test"].output)
}

/**
 * Locates or creates `javadocJar` task in this [Project].
 *
 * The output of this task is a `jar` archive. The archive contains Javadoc,
 * generated upon Java sources from `main` source set. If Javadoc for Kotlin is also needed,
 * apply the Dokka plugin. It tunes `javadoc` task to generate docs upon Kotlin sources as well.
 */
fun Project.javadocJar(): TaskProvider<Jar> = tasks.getOrCreate("javadocJar") {
    archiveClassifier.set("javadoc")
    val javadocFiles = layout.buildDirectory.files("/docs/javadoc")
    from(javadocFiles)
    dependsOn("javadoc")
}

internal fun TaskContainer.getOrCreate(name: String, init: Jar.() -> Unit): TaskProvider<Jar> =
    if (names.contains(name)) {
        named<Jar>(name)
    } else {
        register<Jar>(name) {
            init()
        }
    }

/**
 * Obtains as a set of [Jar] tasks, output of which is used as Maven artifacts.
 *
 * By default, only a jar with Java compilation output is included into publication. This method
 * registers tasks which produce additional artifacts according to the values of [jarFlags].
 *
 * @return the list of the registered tasks.
 */
internal fun Project.artifacts(jarFlags: JarFlags): Set<TaskProvider<Jar>> {
    val tasks = mutableSetOf<TaskProvider<Jar>>()

    if (jarFlags.sourcesJar) {
        tasks.add(sourcesJar())
    }

    if (jarFlags.javadocJar) {
        tasks.add(javadocJar())
    }

    // We don't want to have an empty "proto.jar" when a project doesn't have any Proto files.
    if (hasProto() && jarFlags.publishProtoJar) {
        tasks.add(protoJar())
    }

    // Here, we don't have the corresponding `hasTests()` check, since this artifact is disabled
    // by default. And turning it on means "We have tests and need them to be published."
    if (jarFlags.publishTestJar) {
        tasks.add(testJar())
    }

    if (jarFlags.publishDokkaKotlinJar) {
        tasks.add(dokkaKotlinJar())
    }

    return tasks
}
