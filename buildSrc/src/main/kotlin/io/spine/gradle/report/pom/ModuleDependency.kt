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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

/**
 * A module's dependency.
 *
 * Contains information about a module and configuration, from which
 * the dependency comes.
 */
internal class ModuleDependency(
    val project: Project,
    val configuration: Configuration,
    private val dependency: Dependency,
    private val factualVersion: String? = dependency.version

) : Dependency by dependency, Comparable<ModuleDependency> {

    companion object {
        private val COMPARATOR = compareBy<ModuleDependency> { it.project }
            .thenBy { it.configuration.name }
            .thenBy { it.group }
            .thenBy { it.name }
            .thenBy { it.factualVersion }
    }

    override fun getVersion(): String? = factualVersion

    /**
     * A project dependency with its [scope][DependencyScope].
     *
     * Doesn't contain any info about an origin module and configuration.
     */
    val scoped = ScopedDependency.of(this, configuration)

    /**
     * GAV coordinates of this dependency.
     *
     * Gradle's [Dependency] is a mutable object. Its properties can change their
     * values with time. In particular, the version can be changed as more
     * configurations are getting resolved. This is why this property is calculated.
     */
    val gav: String
        get() = "$group:$name:$factualVersion"

    override fun compareTo(other: ModuleDependency): Int = COMPARATOR.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModuleDependency

        if (project != other.project) return false
        if (configuration != other.configuration) return false
        if (gav != other.gav) return false

        return true
    }

    override fun hashCode(): Int {
        var result = project.hashCode()
        result = 31 * result + configuration.hashCode()
        result = 31 * result + gav.hashCode()
        return result
    }
}
