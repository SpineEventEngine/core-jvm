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

import groovy.xml.MarkupBuilder
import io.spine.gradle.VersionComparator
import java.io.Writer
import java.util.*
import kotlin.reflect.full.isSubclassOf
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.internal.artifacts.dependencies.AbstractExternalModuleDependency
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * Writes the dependencies of a Gradle project in a `pom.xml` format.
 *
 * Includes the dependencies of the subprojects. Does not include
 * the transitive dependencies.
 *
 * ```
 *  <dependencies>
 *      <dependency>
 *          <groupId>io.spine</groupId>
 *          <artifactId>base</artifactId>
 *          <version>2.0.0-pre1</version>
 *      </dependency>
 *      ...
 *  </dependencies>
 * ```
 *
 * The version reported for each dependency is the one selected by Gradle's
 * dependency resolution — the version actually placed on the classpath — rather
 * than the version requested in the build script. This reflects `force(...)`
 * directives, platform/BOM constraints, and conflict resolution.
 *
 * When there are several versions of the same dependency, only the one with
 * the newest version is retained. If the retained version is used in several
 * configurations, the highest-ranking Maven scope is reported, e.g. `compile`
 * wins over `test`.
 *
 * @see PomGenerator
 */
internal class DependencyWriter
private constructor(
    private val dependencies: SortedSet<ScopedDependency>
) {
    internal companion object {

        /**
         * Creates the `DependencyWriter` for the passed [project].
         */
        fun of(project: Project): DependencyWriter {
            return DependencyWriter(project.dependencies())
        }
    }

    /**
     * Writes the dependencies in their `pom.xml` format to the passed [out] writer.
     *
     * The used writer will not be closed.
     */
    fun writeXmlTo(out: Writer) {
        val xml = MarkupBuilder(out)
        xml.withGroovyBuilder {
            "dependencies" {
                dependencies.forEach { scopedDep ->
                    val dependency = scopedDep.dependency()
                    "dependency" {
                        "groupId" { xml.text(dependency.group) }
                        "artifactId" { xml.text(dependency.name) }
                        // A BOM-managed dependency carries no explicit version.
                        // Omit the element rather than emit `<version>null</version>`,
                        // since `null` is not a valid Maven version.
                        dependency.version?.let { version ->
                            "version" { xml.text(version) }
                        }
                        if (scopedDep.hasDefinedScope()) {
                            "scope" { xml.text(scopedDep.scopeName()) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns the [scoped dependencies][ScopedDependency] of a Gradle project.
 *
 * The version of each dependency is the one selected by dependency resolution
 * for the project it comes from. See [resolvedVersions].
 */
fun Project.dependencies(): SortedSet<ScopedDependency> =
    collectScopedDependencies { it.resolvedVersions() }

/**
 * Returns the [scoped dependencies][ScopedDependency] of a Gradle project, taking
 * the version of each dependency from the given [resolvedVersions] map instead of
 * resolving the project's own configurations.
 *
 * This overload exists for tests: a project created with `ProjectBuilder` cannot
 * resolve its configurations against real repositories, so the resolved versions
 * are supplied directly. The keys are the `"group:name"` of the modules.
 */
internal fun Project.dependencies(
    resolvedVersions: Map<String, String>
): SortedSet<ScopedDependency> =
    collectScopedDependencies { resolvedVersions }

/**
 * Collects the [scoped dependencies][ScopedDependency] of this project and its
 * subprojects, deduplicates them, and returns them in the conventional Maven order.
 *
 * The version of each dependency is taken from the map returned by the supplied
 * `resolvedVersionsOf` function for the project the dependency comes from.
 */
private fun Project.collectScopedDependencies(
    resolvedVersionsOf: (Project) -> Map<String, String>
): SortedSet<ScopedDependency> {
    val dependencies = mutableSetOf<ModuleDependency>()
    dependencies.addAll(depsFromAllConfigurations(resolvedVersionsOf(this)))

    subprojects.forEach { subproject ->
        val subprojectDeps = subproject.depsFromAllConfigurations(resolvedVersionsOf(subproject))
        dependencies.addAll(subprojectDeps)
    }

    return deduplicate(dependencies)
        .map { it.scoped }
        .toSortedSet()
}

/**
 * Returns the external dependencies of the project from all the project configurations.
 *
 * The version of each returned dependency is taken from [resolvedVersions] by its
 * `"group:name"` key, falling back to the declared version when the module is on no
 * resolvable configuration — for example, a version managed by a BOM, which carries
 * no explicit version of its own.
 */
private fun Project.depsFromAllConfigurations(
    resolvedVersions: Map<String, String>
): Set<ModuleDependency> {
    val result = mutableSetOf<ModuleDependency>()
    configurations.forEach { configuration ->
        configuration.dependencies
            .filter { it.isExternal() }
            .forEach { dependency ->
                val version = resolvedVersions[moduleKey(dependency.group, dependency.name)]
                    ?: dependency.version
                val moduleDependency =
                    ModuleDependency(this, configuration, dependency, factualVersion = version)
                result.add(moduleDependency)
            }
    }
    return result
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
 * Only resolvable configurations contribute. When a module resolves to different
 * versions across configurations, the newest one (by [VersionComparator]) is kept,
 * matching the deduplication applied afterwards. A configuration that fails to
 * resolve in isolation is skipped and logged, so the report never breaks the build.
 */
private fun Project.resolvedVersions(): Map<String, String> {
    // Resolving an individual configuration may fail for reasons unrelated to the
    // report — missing repositories for a niche configuration, an unsatisfiable
    // constraint, and the like. Such a configuration contributes no versions.
    @Suppress("TooGenericExceptionCaught") // Any resolution failure is non-fatal here.
    fun componentsOf(configuration: Configuration): Set<ResolvedComponentResult> =
        try {
            configuration.incoming.resolutionResult.allComponents
        } catch (e: Exception) {
            logger.info(
                "Skipping configuration `${configuration.name}` " +
                    "while collecting resolved dependency versions.",
                e
            )
            emptySet()
        }

    return configurations
        .filter { it.isCanBeResolved }
        .flatMap { componentsOf(it) }
        .mapNotNull { it.moduleVersion }
        .groupBy { moduleKey(it.group, it.name) }
        .mapValues { (_, versions) -> versions.maxOfWith(VersionComparator) { it.version } }
}

/**
 * Builds the `"group:name"` key under which a module's resolved version is recorded
 * and looked up.
 *
 * Forming the key in one place keeps the lookup in [depsFromAllConfigurations]
 * consistent with what [resolvedVersions] records and with the grouping done by
 * [deduplicate].
 */
private fun moduleKey(group: String?, name: String): String = "$group:$name"

/**
 * Tells whether the dependency is an external module dependency.
 */
private fun Dependency.isExternal(): Boolean {
    return this.javaClass.kotlin.isSubclassOf(AbstractExternalModuleDependency::class)
}

/**
 * Filters out duplicated dependencies by group and name.
 *
 * When there are several versions of the same dependency, the method will retain only
 * the one with the newest version.
 *
 * Sometimes, a project uses several versions of the same dependency. This may happen
 * when different modules of the project use different versions of the same dependency.
 * But for our `pom.xml`, which has clearly representative character, a single version
 * of a dependency is quite enough.
 *
 * Versions are compared by [VersionComparator] rather than as plain text, so `10.0.0`
 * is recognized as newer than `9.2.0`, and `2.0.0-SNAPSHOT.100` — as newer
 * than `2.0.0-SNAPSHOT.99`.
 *
 * When the newest version comes from several configurations, the occurrence with
 * the highest-ranking Maven scope (as defined by [ScopedDependency.dependencyPriority])
 * is retained. For example, a dependency declared via `api` in one module and via
 * `testImplementation` in another is reported with the `compile` scope, so a production
 * dependency is not misrepresented as a test-scoped one. Likewise, an artifact coming
 * from `compileOnly` or `annotationProcessor` in one module and from a test
 * configuration in another is reported as `provided`.
 *
 * The rejected duplicates are logged.
 */
private fun Project.deduplicate(dependencies: Set<ModuleDependency>): List<ModuleDependency> {
    val groups = dependencies.groupBy { moduleKey(it.group, it.name) }

    logDuplicates(groups.mapValues { (_, deps) -> deps.distinctBy { it.gav } })

    val filtered = groups.values.map { sameArtifact ->
        val newest = sameArtifact.maxWith(compareBy(VersionComparator) { it.version ?: "" })
        sameArtifact.filter { it.version == newest.version }
            .minBy { it.scoped.dependencyPriority() }
    }
    return filtered
}

private fun Project.logDuplicates(dependencies: Map<String, List<ModuleDependency>>) {
    dependencies.filter { it.value.size > 1 }
        .forEach { (dependency, versions) -> logDuplicate(dependency, versions) }
}

private fun Project.logDuplicate(dependency: String, versions: List<ModuleDependency>) {
    logger.lifecycle("")
    logger.lifecycle("The project uses several versions of `$dependency` dependency.")

    versions.forEach {
        logger.lifecycle(
            "module: {}, configuration: {}, version: {}",
            it.project.name,
            it.configuration.name,
            it.version
        )
    }
}
