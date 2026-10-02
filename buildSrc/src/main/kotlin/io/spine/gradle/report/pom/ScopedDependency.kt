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

import io.spine.gradle.report.pom.DependencyScope.compile
import io.spine.gradle.report.pom.DependencyScope.provided
import io.spine.gradle.report.pom.DependencyScope.runtime
import io.spine.gradle.report.pom.DependencyScope.system
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
         * Performs comparison of `ScopedDependency` instances according to these rules:
         *
         *  * Compares the scope of the dependency first. Dependency with a lower scope priority
         *  number goes first.
         *
         *  * For dependencies with the **same scope** does the lexicographical group
         *  name comparison.
         *
         *  * For dependencies within the **same group**, does the lexicographical artifact
         *  name comparison.
         *
         *  * For dependencies with the **same artifact name**, does the lexicographical artifact
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

    /** Obtains the scope name of this dependency. */
    fun scopeName(): String {
        return scope.name
    }

    /**
     * Obtains the layout priority of a scope.
     *
     * Layout priority determines what scopes come first in the generated `pom.xml` file.
     * Dependencies with a lower priority number go on top, following the conventional
     * Maven scope order: `compile`, `provided`, `runtime`, `test`, and `system`.
     * Dependencies with an undefined scope go last.
     *
     * The same ordering also drives the scope selection when the same dependency
     * comes from several configurations: the occurrence with the lowest priority
     * number is reported. So, a scope required by production code wins over `test`,
     * and a known scope wins over an undefined one.
     */
    @Suppress("MagicNumber") // Reason: the values encode the relative scope order.
    internal fun dependencyPriority(): Int {
        return when (scope) {
            compile -> 0
            provided -> 1
            runtime -> 2
            test -> 3
            system -> 4
            undefined -> 5
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
