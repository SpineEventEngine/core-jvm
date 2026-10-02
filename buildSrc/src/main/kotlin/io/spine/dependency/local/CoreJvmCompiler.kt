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

package io.spine.dependency.local

/**
 * Dependencies on the CoreJvm Compiler artifacts.
 *
 * See [CoreJvm Compiler](https://github.com/SpineEventEngine/core-jvm-compiler).
 */
@Suppress(
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */,
    "ConstPropertyName" /* We use a custom convention for artifact properties. */,
    "unused"
)
object CoreJvmCompiler {

    /**
     * The Compiler belongs to the `tools` group.
     */
    const val group = Spine.toolsGroup

    /**
     * The version used in the build classpath.
     */
    const val dogfoodingVersion = "2.0.0-SNAPSHOT.093"

    /**
     * The version to be used for integration tests.
     */
    const val version = "2.0.0-SNAPSHOT.093"

    /**
     * The ID of the Gradle plugin.
     */
    const val pluginId = "io.spine.core-jvm"

    /**
     * The name of the published artifact with the CoreJvm Gradle Plugin.
     *
     * The POM of this artifact declares a runtime dependency on
     * [the Compiler plugins][compilerPluginsArtifact].
     */
    const val gradlePluginArtifact = "core-jvm-gradle-plugin"

    /**
     * The name of the published artifact with the CoreJvm Compiler plugins.
     */
    const val compilerPluginsArtifact = "core-jvm-plugins"

    /**
     * The CoreJvm Gradle Plugin library with the [dogfoodingVersion].
     */
    val gradlePlugin: String = gradlePlugin(dogfoodingVersion)

    /**
     * The CoreJvm Gradle Plugin library with the given [version].
     */
    fun gradlePlugin(version: String): String = "$group:$gradlePluginArtifact:$version"

    /**
     * The library with the CoreJvm Compiler plugins with the [version].
     */
    val compilerPlugins: String = compilerPlugins(version)

    /**
     * The library with the CoreJvm Compiler plugins with the given [version].
     */
    fun compilerPlugins(version: String): String = "$group:$compilerPluginsArtifact:$version"
}
