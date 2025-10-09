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

package io.spine.dependency

import io.spine.gradle.log
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy

/**
 * A dependency is a software component we use in a project.
 *
 * It could be a library, a set of libraries, or a development tool
 * that participates in a build.
 */
abstract class Dependency {

    /**
     * The version of the dependency in terms of Maven coordinates.
     */
    abstract val version: String

    /**
     * The group of the dependency in terms of Maven coordinates.
     */
    abstract val group: String

    /**
     * The modules of the dependency that we use directly or
     * transitively in our projects.
     */
    abstract val modules: List<String>

    /**
     * The [modules] given with the [version].
     */
    final val artifacts: Map<String, String> by lazy {
        modules.associateWith { "$it:$version" }
    }

    /**
     * Obtains full Maven coordinates for the requested [module].
     */
    fun artifact(module: String): String = artifacts[module] ?: error(
        "The dependency `${this::class.simpleName}` does not declare a module `$module`."
    )

    /**
     * Forces all artifacts of this dependency using the given resolution strategy.
     *
     * @param project The project in which the artifacts are forced. Used for logging.
     * @param cfg The configuration for which the artifacts are forced.  Used for logging.
     * @param rs The resolution strategy which forces the artifacts.
     */
    fun forceArtifacts(project: Project, cfg: Configuration, rs: ResolutionStrategy) {
        artifacts.values.forEach {
            rs.forceWithLogging(project, cfg, it)
        }
    }
}

/**
 * A dependency which declares a Maven Bill of Materials (BOM).
 *
 * @see <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Bill_of_Materials_.28BOM.29_POMs">
 * Maven Bill of Materials</a>
 * @see io.spine.dependency.boms.Boms
 * @see io.spine.dependency.boms.BomsPlugin
 */
abstract class DependencyWithBom : Dependency() {

    /**
     * Maven coordinates of the dependency BOM.
     */
    abstract val bom: String
}

/**
 * Returns the suffix of diagnostic messages for this configuration in the given project.
 */
fun Configuration.diagSuffix(project: Project): String =
    "the configuration `$name` in the project: `${project.path}`."


private fun ResolutionStrategy.forceWithLogging(
    project: Project,
    configuration: Configuration,
    artifact: String
) {
    force(artifact)
    project.log { "Forced the version of `$artifact` in " + configuration.diagSuffix(project) }
}

/**
 * Obtains full Maven coordinates for the requested [module].
 *
 * This extension allows referencing properties of the [Dependency],
 * upon which it is invoked.
 *
 * An example usage:
 *
 * ```
 * // Supposing there is `Ksp.symbolProcessingApi: String` property declared.
 * Ksp.artifact { symbolProcessingApi }
 * ```
 */
fun <T : Dependency> T.artifact(module: T.() -> String): String =
    artifact(module())
