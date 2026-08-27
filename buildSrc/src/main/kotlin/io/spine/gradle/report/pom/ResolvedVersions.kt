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

package io.spine.gradle.report.pom

import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.VersionComparator
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Stores the versions selected by dependency resolution, per project.
 *
 * Gradle forbids resolving the configurations of one project from a task of
 * another: a task holds the lock of its own project only, and cross-project
 * resolution fails with `IllegalResolutionException`. So the `generatePom` task
 * of the root project cannot resolve the configurations of subprojects itself.
 * Instead, [a task][registerTaskIn] is registered for every project, resolving
 * only the configurations of its own project — which is always lock-safe — and
 * storing the result under the project's build directory. `generatePom` depends
 * on these tasks and [reads their outputs][readFrom].
 *
 * This makes the report independent of which other tasks run in the same Gradle
 * invocation. Reading `resolutionResult` from the root task directly worked only
 * when a previous task of the owning project had already resolved the configuration,
 * so the report differed between `gradle build` and `gradle generatePom`.
 *
 * @see PomGenerator
 */
internal object ResolvedVersions {

    /**
     * The name of the per-project task registered by [registerTaskIn].
     */
    const val taskName = "collectResolvedVersions"

    /**
     * The path to the output file of the [taskName] task within
     * the build directory of its project.
     */
    private const val relativePath = "pom/resolved-versions.txt"

    /**
     * Registers the [taskName] task in the given [project].
     *
     * The task resolves the resolvable configurations of this project only and
     * writes the [resolved versions][resolvedVersions] to a file under the
     * project's build directory, one `group:name=version` line per module.
     *
     * The task declares no inputs or outputs on purpose: it runs on every
     * invocation, so the stored versions always reflect the current build
     * scripts rather than a previously cached state.
     *
     * The task orders itself after `clean`: Gradle does not order the two
     * otherwise, so in a `gradle clean build` invocation a late-running `clean`
     * could delete a freshly written file.
     */
    fun registerTaskIn(project: Project): TaskProvider<Task> =
        project.tasks.register(taskName) {
            group = SpineTaskGroup.name
            description = "Collects the versions of dependencies of " +
                    "the `${project.name}` project selected by dependency resolution"
            mustRunAfter(project.tasks.matching { it.name == "clean" })
            doLast {
                val file = outputFileIn(project)
                file.parentFile.mkdirs()
                file.writeText(serialize(project.resolvedVersions()))
            }
        }

    /**
     * Reads the versions stored by the [taskName] task of the given [project],
     * keyed by the `"group:name"` of each module.
     *
     * Returns an empty map when the task has not run, e.g., for a project
     * with no resolvable configurations in a test environment.
     */
    fun readFrom(project: Project): Map<String, String> {
        val file = outputFileIn(project)
        if (!file.exists()) {
            return emptyMap()
        }
        return file.readLines()
            .filter { it.isNotBlank() }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    private fun outputFileIn(project: Project): File =
        project.layout.buildDirectory.file(relativePath).get().asFile

    private fun serialize(versions: Map<String, String>): String =
        versions.entries
            .sortedBy { it.key }
            .joinToString(separator = "\n", postfix = "\n") { (module, version) ->
                "$module=$version"
            }
}

/**
 * Returns the versions selected by dependency resolution for this project, keyed
 * by the `"group:name"` of each module.
 *
 * The declared version of a dependency is what the build script *requested*, which
 * may differ from what the build *uses*: a `force(...)`, a platform/BOM constraint,
 * or Gradle's conflict resolution can all select another version. Reading the
 * resolution result captures the selected version, so the report describes the
 * dependencies actually on the classpath rather than the requested ones.
 *
 * Only resolvable configurations of this project contribute. When a module resolves
 * to different versions across configurations, the newest one (by [VersionComparator])
 * is kept, matching the deduplication applied by [DependencyWriter] afterwards.
 *
 * Reading a resolution graph is lenient: a module that cannot be resolved — be it
 * missing from the repositories, or a casualty of `failOnVersionConflict()` —
 * becomes an `UnresolvedDependencyResult` edge and simply contributes no version,
 * never an exception. So the report cannot break the build, and no failure needs
 * to be — or is — swallowed here: anything actually thrown is unexpected and
 * fails the collecting task loudly.
 *
 * Must be called either from a task of this very project, or before the task
 * execution starts — otherwise Gradle rejects the resolution as unsafe.
 */
internal fun Project.resolvedVersions(): Map<String, String> =
    configurations
        .filter { it.isCanBeResolved }
        .flatMap { it.incoming.resolutionResult.allComponents }
        .mapNotNull { it.moduleVersion }
        .groupBy { moduleKey(it.group, it.name) }
        .mapValues { (_, versions) -> versions.maxOfWith(VersionComparator) { it.version } }
