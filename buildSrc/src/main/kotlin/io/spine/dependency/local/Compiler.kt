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

import io.spine.dependency.Dependency

/**
 * Dependencies on the Spine Compiler modules.
 *
 * To use a locally published Compiler version instead of the version from a public plugin
 * registry, set the `COMPILER_VERSION` and/or the `COMPILER_DF_VERSION` environment variables
 * and stop the Gradle daemons so that Gradle observes the env change:
 * ```
 * export COMPILER_VERSION=0.43.0-local
 * export COMPILER_DF_VERSION=0.41.0
 *
 * ./gradle --stop
 * ./gradle build   # Conduct the intended checks.
 * ```
 *
 * Then, to reset the console to run the usual versions again, remove the values of
 * the environment variables and stop the daemon:
 * ```
 * export COMPILER_VERSION=""
 * export COMPILER_DF_VERSION=""
 *
 * ./gradle --stop
 * ```
 *
 * See [`SpineEventEngine/compiler`](https://github.com/SpineEventEngine/compiler/).
 */
@Suppress(
    "unused" /* Some subprojects do not use the Compiler directly. */,
    "ConstPropertyName" /* We use a custom convention for artifact properties. */,
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */,
)
object Compiler : Dependency() {
    const val pluginGroup = Spine.group
    override val group = Spine.toolsGroup
    const val pluginId = "io.spine.compiler"

    /**
     * Identifies the Compiler as a `classpath` dependency under the `buildScript` block.
     */
    const val module = "io.spine.tools:compiler"

    /**
     * The version of the Compiler dependencies.
     */
    override val version: String
    private const val fallbackVersion = "2.0.0-SNAPSHOT.069"

    /**
     * The distinct version of the Compiler used by other build tools.
     *
     * When the Compiler is used both for building the project and as a part of the Project's
     * transitive dependencies, this is the version used to build the project itself.
     */
    val dogfoodingVersion: String
    private const val fallbackDfVersion = "2.0.0-SNAPSHOT.069"

    /**
     * The artifact for the Compiler Gradle plugin.
     */
    val pluginLib: String

    /**
     * The artifact to be used during experiments when publishing locally.
     *
     * @see Compiler
     */
    fun pluginLib(version: String): String =
        "$group:compiler-gradle-plugin:$version"

    fun api(version: String): String =
        "$group:compiler-api:$version"

    val api
        get() = api(version)

    val backend
        get() = "$group:compiler-backend:$version"

    val params
        get() = "$group:compiler-params:$version"

    val protocPlugin
        get() = "$group:compiler-protoc-plugin:$version"

    val gradleApi
        get() = "$group:compiler-gradle-api:$version"

    val jvmModule = "$group:compiler-jvm"

    fun jvm(version: String): String =
        "$jvmModule:$version"

    val jvm
        get() = jvm(version)

    /**
     * The all-in-one ("fat") distribution of the Compiler command-line application.
     *
     * The artifact is named `compiler-cli-all` because it is published by
     * the `cliFatJar` publication of the `:cli` module. Keep this name in sync with
     * the `Artifacts.fatCli()` function of the `compiler-gradle-api` module.
     */
    val fatCli
        get() = "$group:compiler-cli-all:$version"

    val testlib
        get() = "$group:compiler-testlib:$version"

    override val modules: List<String>
        get() = listOf(
            api,
            backend,
            params,
            protocPlugin,
            gradleApi,
            jvm,
            fatCli,
            testlib
        ).map {
            it.split(":").let { (group, artifact) -> "$group:$artifact" }
        }

    /**
     * An env variable storing a custom [version].
     */
    private const val VERSION_ENV = "COMPILER_VERSION"

    /**
     * An env variable storing a custom [dogfoodingVersion].
     */
    private const val DF_VERSION_ENV = "COMPILER_DF_VERSION"

    /**
     * Sets up the versions and artifacts for the build to use.
     *
     * If either [VERSION_ENV] or [DF_VERSION_ENV] is set, those versions are used instead of
     * the hardcoded ones. Also, in this mode, the [pluginLib] coordinates are changed so that
     * it points at a locally published artifact. Otherwise, it points at an artifact that would be
     * published to a public plugin registry.
     */
    init {
        val experimentVersion = System.getenv(VERSION_ENV)
        val experimentDfVersion = System.getenv(DF_VERSION_ENV)
        if (experimentVersion?.isNotBlank() == true || experimentDfVersion?.isNotBlank() == true) {
            version = experimentVersion ?: fallbackVersion
            dogfoodingVersion = experimentDfVersion ?: fallbackDfVersion

            pluginLib = pluginLib(version)
            println("""

                ❗ Running an experiment with the Spine Compiler. ❗
                -----------------------------------------
                    Regular version     = v$version
                    Dogfooding version  = v$dogfoodingVersion

                    The Compiler Gradle plugin can now be loaded from Maven Local.

                    To reset the versions, erase the `$$VERSION_ENV` and `$$DF_VERSION_ENV` environment variables.

            """.trimIndent())
        } else {
            version = fallbackVersion
            dogfoodingVersion = fallbackDfVersion
            pluginLib = pluginLib(version)
        }
    }
}
