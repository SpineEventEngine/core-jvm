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
 * Artifacts of the `tool-base` repository.
 *
 * The repository no longer publishes a module of its own name. Its former
 * contents are split across the focused modules declared below.
 *
 * @see <a href="https://github.com/SpineEventEngine/tool-base">tool-base</a>
 */
@Suppress("ConstPropertyName", "unused")
object ToolBase {
    const val group = Spine.toolsGroup
    const val version = "2.0.0-SNAPSHOT.423"
    const val dogfoodingVersion = "2.0.0-SNAPSHOT.423"

    /**
     * The former all-in-one module, split into the focused modules below.
     *
     * The artifact is no longer published as of `2.0.0-SNAPSHOT.420`. Replace it
     * with the modules a project actually uses: [archive], [code], [fs],
     * [javaCode], [kotlinCode], or [protoCode].
     *
     * `io.spine.tools.OsFamily`, which this module also carried, now lives in
     * Base Libraries as `io.spine.environment.OsFamily`.
     */
    @Deprecated("The `tool-base` artifact is no longer published. Use the module you need.")
    const val lib = "$group:tool-base:$version"

    const val archive = "$group:archive:$version"
    const val code = "$group:code:$version"
    const val fs = "$group:fs:$version"

    const val javaCode = "$group:java-code:$version"
    const val kotlinCode = "$group:kotlin-code:$version"
    const val protoCode = "$group:proto-code:$version"

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
