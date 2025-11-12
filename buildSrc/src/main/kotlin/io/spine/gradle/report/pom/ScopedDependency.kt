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

import io.spine.gradle.report.pom.DependencyScope.compile
import io.spine.gradle.report.pom.DependencyScope.provided
import io.spine.gradle.report.pom.DependencyScope.runtime
import io.spine.gradle.report.pom.DependencyScope.test
import io.spine.gradle.report.pom.DependencyScope.undefined
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

/**
 * A project dependency with its [scope][DependencyScope].
 *
 * See [More on dependency scopes](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope).
 */
class ScopedDependency
private constructor(
    private val dependency: Dependency,
    private val scope: DependencyScope
) : Comparable<ScopedDependency> {

    internal companion object {

        /**
         * A map that contains the relations of known Gradle configuration names
         * to their Maven dependency scope equivalents.
         */
        private val CONFIG_TO_SCOPE = mapOf(

            /**
             * Configurations from the Gradle Java plugin that are known to be mapped
             * to the `compile` scope.
             *
             * Dependencies with the `compile` Maven scope are propagated to dependent projects.
             *
             * More on that in the [Gradle docs](https://docs.gradle.org/current/userguide/java_plugin.html#tab:configurations).
             */
            "compile" to compile,
            "implementation" to compile,
            "api" to compile,

            /**
             * Configurations from the Gradle Java plugin that are known to be mapped
             * to the `runtime` scope.
             *
             * Dependencies with the `runtime` Maven scopes are required for execution only.
             */
            "runtime" to runtime,
            "runtimeOnly" to runtime,
            "runtimeClasspath" to runtime,
            "default" to runtime,

            /**
             * Configurations from the Gradle Java plugin that are known to be mapped
             * to the `provided` scope.
             *
             * Dependencies with the `provided` Maven scope are not propagated to dependent projects
             * but are required during the compilation.
             */
            "compileOnly" to provided,
            "compileOnlyApi" to provided,
            "annotationProcessor" to provided
        )

        /**
         * Creates a `ScopedDependency` for the given [dependency]
         * judging on the passed [configuration].
         */
        fun of(dependency: Dependency, configuration: Configuration): ScopedDependency {
            val configurationName = configuration.name
            val knownScope = CONFIG_TO_SCOPE[configurationName]
            return when {
                knownScope != null -> ScopedDependency(dependency, knownScope)
                isTestsRelated(configurationName) -> ScopedDependency(dependency, test)
                else -> ScopedDependency(dependency, undefined)
            }
        }

        private fun isTestsRelated(configurationName: String): Boolean =
            configurationName.startsWith("test", ignoreCase = true)

        /**
         * Performs comparison of {@code DependencyWithScope} instances according to these rules:
         *
         * * Compares the scope of the dependency first. Dependency with lower scope priority
         * number goes first.
         *
         *  * For dependencies with same scope, does the lexicographical group name comparison.
         *
         *  * For dependencies within the same group, does the lexicographical artifact
         *  name comparison.
         *
         *  * For dependencies with the same artifact name, does the lexicographical artifact
         *  version comparison.
         */
        private val COMPARATOR: Comparator<ScopedDependency> =
            compareBy<ScopedDependency> { it.dependencyPriority() }
                .thenBy { it.dependency.group }
                .thenBy { it.dependency.name }
                .thenBy { it.dependency.version }
    }

    /**
     * Returns `true` if this dependency has a defined scope, returns `false` otherwise.
     */
    fun hasDefinedScope(): Boolean {
        return scope != undefined
    }

    /** Obtains the Gradle dependency. */
    fun dependency(): Dependency {
        return dependency
    }

    /** Obtains the scope name of this dependency .*/
    fun scopeName(): String {
        return scope.name
    }

    /**
     * Obtains the layout priority of a scope.
     *
     * Layout priority determines what scopes come first in the generated `pom.xml` file.
     * Dependencies with a lower priority number go on top.
     */
    internal fun dependencyPriority(): Int {
        return when (scope) {
            compile -> 0
            runtime -> 1
            test -> 2
            else -> 3
        }
    }

    override fun compareTo(other: ScopedDependency): Int {
        return COMPARATOR.compare(this, other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScopedDependency) return false

        if (dependency.group != other.dependency.group) return false
        if (dependency.name != other.dependency.name) return false
        if (dependency.version != other.dependency.version) return false

        return true
    }

    override fun hashCode(): Int {
        return dependency.hashCode()
    }
}
