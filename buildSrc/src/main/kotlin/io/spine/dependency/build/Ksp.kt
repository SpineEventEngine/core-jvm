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

package io.spine.dependency.build

import io.spine.dependency.Dependency

/**
 * Kotlin Symbol Processing API.
 *
 * @see <a href="https://github.com/google/ksp">KSP GitHub repository</a>
 */
@Suppress("unused")
object Ksp : Dependency() {
    override val version = "2.3.11"
    val dogfoodingVersion = version
    override val group = "com.google.devtools.ksp"

    const val id = "com.google.devtools.ksp"
    const val gradlePluginArtifactName = "com.google.devtools.ksp.gradle.plugin"

    /**
     * The plugin marker of the KSP Gradle plugin, through which Gradle resolves
     * the plugin by its [id].
     *
     * The marker is not among the [modules]: it is a POM without code,
     * which points at [gradlePlugin].
     */
    val gradlePluginMarker = "$id:$gradlePluginArtifactName"

    /** Returns the coordinates of [gradlePluginMarker] with the [version]. */
    fun gradlePluginMarker(): String = artifact(gradlePluginMarker, version)

    val symbolProcessingApi = "$group:symbol-processing-api"

    /** Returns the coordinates of [symbolProcessingApi] with the [version]. */
    fun symbolProcessingApi(): String = artifact(symbolProcessingApi)

    val symbolProcessing = "$group:symbol-processing"

    /** Returns the coordinates of [symbolProcessing] with the [version]. */
    fun symbolProcessing(): String = artifact(symbolProcessing)

    val symbolProcessingAaEmb = "$group:symbol-processing-aa-embeddable"

    /** Returns the coordinates of [symbolProcessingAaEmb] with the [version]. */
    fun symbolProcessingAaEmb(): String = artifact(symbolProcessingAaEmb)

    val symbolProcessingCommonDeps = "$group:symbol-processing-common-deps"

    /** Returns the coordinates of [symbolProcessingCommonDeps] with the [version]. */
    fun symbolProcessingCommonDeps(): String = artifact(symbolProcessingCommonDeps)

    val gradlePlugin = "$group:symbol-processing-gradle-plugin"

    /** Returns the coordinates of [gradlePlugin] with the [version]. */
    fun gradlePlugin(): String = artifact(gradlePlugin)

    override val modules = listOf(
        symbolProcessingApi,
        symbolProcessing,
        symbolProcessingAaEmb,
        symbolProcessingCommonDeps,
        gradlePlugin,
    )
}
