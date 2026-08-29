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
    const val dogfoodingVersion = "2.0.0-SNAPSHOT.092"

    /**
     * The version to be used for integration tests.
     */
    const val version = "2.0.0-SNAPSHOT.092"

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
