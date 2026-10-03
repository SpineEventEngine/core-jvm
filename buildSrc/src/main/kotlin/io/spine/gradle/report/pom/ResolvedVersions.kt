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
            // Filtered by name rather than by task: `matching` tests a predicate
            // against each task, so it has to instantiate every one registered in
            // the project merely to read its name.
            mustRunAfter(project.tasks.named { it == "clean" })
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
