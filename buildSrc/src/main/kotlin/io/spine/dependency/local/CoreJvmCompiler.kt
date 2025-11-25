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

package io.spine.dependency.local

/**
 * Dependencies on the CoreJvm Compiler artifacts.
 *
 * See [mc-java](https://github.com/SpineEventEngine/core-jvm-compiler).
 */
@Suppress(
    "MemberVisibilityCanBePrivate" /* `pluginLib()` is used by subprojects. */,
    "ConstPropertyName",
    "unused"
)
object CoreJvmCompiler {

    /**
     * The Compiler belongs to the `tools` group.
     */
    const val group = Spine.toolsGroup

    /**
     * The version used to in the build classpath.
     */
    const val dogfoodingVersion = "2.0.0-SNAPSHOT.024"

    /**
     * The version to be used for integration tests.
     */
    const val version = "2.0.0-SNAPSHOT.024"

    /**
     * The ID of the Gradle plugin.
     */
    const val pluginId = "io.spine.core-jvm"

    /**
     * The library with the [dogfoodingVersion].
     */
    val pluginLib = pluginLibNew(dogfoodingVersion)

    /**
     * The library with the given [version].
     *
     * This is the notation before the version `2.0.0-SNAPSHOT.013`
     */
    @Deprecated("Use `pluginLibNew()` instead.")
    fun pluginLib(version: String): String = "$group:core-jvm-plugins:$version:all"

    /**
     * The library with the given [version].
     *
     * @since 2.0.0-SNAPSHOT.013
     */
    fun pluginLibNew(version: String): String = "$group:core-jvm-plugins:$version"

    /** The artifact reference for forcing in configurations. */
    const val pluginsArtifact: String = "$group:core-jvm-plugins:$version"

    /**
     * The `core-jvm-base` artifact with the [version].
     */
    val base = base(version)

    /**
     * The `core-jvm-base` artifact with the given [version].
     */
    fun base(version: String): String = "$group:core-jvm-base:$version"
}
