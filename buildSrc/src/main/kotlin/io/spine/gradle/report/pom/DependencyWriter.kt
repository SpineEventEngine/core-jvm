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

package io.spine.gradle.report.pom

import groovy.xml.MarkupBuilder
import java.io.Writer
import java.util.*
import kotlin.reflect.full.isSubclassOf
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
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
 * When there are several versions of the same dependency, only the one with
 * the newest version is retained.
 *
 * @see PomGenerator
 */
internal class DependencyWriter
private constructor(
    private val dependencies: SortedSet<ScopedDependency>
) {
    internal companion object {

        /**
         * Creates the `ProjectDependenciesAsXml` for the passed [project].
         */
        fun of(project: Project): DependencyWriter {
            return DependencyWriter(project.dependencies())
        }
    }

    /**
     * Writes the dependencies in their `pom.xml` format to the passed [out] writer.
     *
     * <p>Used writer will not be closed.
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
                        "version" { xml.text(dependency.version) }
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
 */
fun Project.dependencies(): SortedSet<ScopedDependency> {
    val dependencies = mutableSetOf<ModuleDependency>()
    dependencies.addAll(this.depsFromAllConfigurations())

    this.subprojects.forEach { subproject ->
        val subprojectDeps = subproject.depsFromAllConfigurations()
        dependencies.addAll(subprojectDeps)
    }

    val result = deduplicate(dependencies)
        .map { it.scoped }
        .toSortedSet()
    return result
}

/**
 * Returns the external dependencies of the project from all the project configurations.
 */
private fun Project.depsFromAllConfigurations(): Set<ModuleDependency> {
    val result = mutableSetOf<ModuleDependency>()
    this.configurations.forEach { configuration ->
        configuration.dependencies
            .filter { it.isExternal() }
            .forEach { dependency ->
                val forcedVersion = configuration.forcedVersionOf(dependency)
                val moduleDependency =
                    if (forcedVersion != null) {
                        ModuleDependency(project, configuration, dependency, forcedVersion)
                    } else {
                        ModuleDependency(project, configuration, dependency)
                    }
                result.add(moduleDependency)
            }
    }
    return result
}

/**
 * Searches for a forced version of given [dependency] in this [Configuration].
 *
 * Returns `null`, if it wasn't forced.
 */
private fun Configuration.forcedVersionOf(dependency: Dependency): String? {
    val forcedModules = resolutionStrategy.forcedModules
    val maybeForced = forcedModules.firstOrNull {
        it.group == dependency.group
                && it.name == dependency.name
                && it.version != null
    }
    return maybeForced?.version
}

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
 * The rejected duplicates are logged.
 */
private fun Project.deduplicate(dependencies: Set<ModuleDependency>): List<ModuleDependency> {
    val groups = dependencies.distinctBy { it.gav }
        .groupBy { it.run { "$group:$name" } }

    logDuplicates(groups)

    val filtered = groups.map { group ->
        group.value.maxByOrNull { dep -> dep.version ?: "" }
    }.filterNotNull()
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
