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
 * Artifacts of the `tool-base` module.
 *
 * @see <a href="https://github.com/SpineEventEngine/tool-base">tool-base</a>
 */
@Suppress("ConstPropertyName", "unused")
object ToolBase {
    const val group = Spine.toolsGroup
    const val version = "2.0.0-SNAPSHOT.370"
    const val dogfoodingVersion = "2.0.0-SNAPSHOT.370"

    const val lib = "$group:tool-base:$version"
    const val classicCodegen = "$group:classic-codegen:$version"
    const val pluginBase = "$group:plugin-base:$version"
    const val pluginTestlib = "$group:plugin-testlib:$version"

    const val intellijPlatform = "$group:intellij-platform:$version"
    const val intellijPlatformJava = "$group:intellij-platform-java:$version"

    const val psi = "$group:psi:$version"
    const val psiJavaArtifactName = "psi-java"
    const val psiJava = "$group:$psiJavaArtifactName:$version"

    const val rootGradlePlugins = "$group:root-gradle-plugins:$version"
    const val gradlePluginApi = "$group:gradle-plugin-api:$version"
    const val gradlePluginApiTestFixtures = "$group:gradle-plugin-api-test-fixtures:$version"

    const val jvmTools = "$group:jvm-tools:$version"
    const val jvmToolPluginDogfooding = "$group:jvm-tool-plugins-all:$dogfoodingVersion"
    const val jvmToolPlugins = "$group:jvm-tool-plugins-all:$version"

    const val protobufSetupPlugins = "$group:protobuf-setup-plugins:$version"

    object JavadocFilter {
        const val group = ToolBase.group
        const val version = "2.0.0-SNAPSHOT.75"
        const val artifact = "$group:spine-javadoc-filter:$version"
    }
}
