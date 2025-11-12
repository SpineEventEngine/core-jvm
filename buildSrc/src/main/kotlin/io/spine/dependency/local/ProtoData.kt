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
 * Dependencies on ProtoData modules.
 *
 * To use a locally published ProtoData version instead of the version from a public plugin
 * registry, set the `PROTODATA_VERSION` and/or the `PROTODATA_DF_VERSION` environment variables
 * and stop the Gradle daemons so that Gradle observes the env change:
 * ```
 * export PROTODATA_VERSION=0.43.0-local
 * export PROTODATA_DF_VERSION=0.41.0
 *
 * ./gradle --stop
 * ./gradle build   # Conduct the intended checks.
 * ```
 *
 * Then, to reset the console to run the usual versions again, remove the values of
 * the environment variables and stop the daemon:
 * ```
 * export PROTODATA_VERSION=""
 * export PROTODATA_DF_VERSION=""
 *
 * ./gradle --stop
 * ```
 *
 * See [`SpineEventEngine/ProtoData`](https://github.com/SpineEventEngine/ProtoData/).
 */
@Suppress(
    "unused" /* Some subprojects do not use ProtoData directly. */,
    "ConstPropertyName" /* We use custom convention for artifact properties. */,
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */,
)
object ProtoData {
    const val pluginGroup = Spine.group
    const val group = "io.spine.protodata"
    const val pluginId = "io.spine.protodata"

    /**
     * Identifies ProtoData as a `classpath` dependency under `buildScript` block.
     *
     * The dependency is obtained from https://plugins.gradle.org/m2/.
     */
    const val module = "io.spine:protodata"

    /**
     * The version of ProtoData dependencies.
     */
    val version: String
    private const val fallbackVersion = "0.96.4"

    /**
     * The distinct version of ProtoData used by other build tools.
     *
     * When ProtoData is used both for building the project and as a part of the Project's
     * transitional dependencies, this is the version used to build the project itself.
     */
    val dogfoodingVersion: String
    private const val fallbackDfVersion = "0.96.4"

    /**
     * The artifact for the ProtoData Gradle plugin.
     */
    val pluginLib: String

    /**
     * The artifact to be used during experiments when publishing locally.
     *
     * @see ProtoData
     */
    private fun pluginLib(version: String): String =
        "$group:gradle-plugin:$version"

    fun api(version: String): String =
        "$group:protodata-api:$version"

    val api
        get() = api(version)

    val backend
        get() = "$group:protodata-backend:$version"

    val params
        get() = "$group:protodata-params:$version"

    val protocPlugin
        get() = "$group:protodata-protoc:$version"

    val gradleApi
        get() = "$group:protodata-gradle-api:$version"

    val cliApi
        get() = "$group:protodata-cli-api:$version"

    val javaModule = "$group:protodata-java"

    fun java(version: String): String =
        "$javaModule:$version"

    val java
        get() = java(version)

    val fatCli
        get() = "$group:protodata-fat-cli:$version"

    val testlib
        get() = "$group:protodata-testlib:$version"

    /**
     * An env variable storing a custom [version].
     */
    private const val VERSION_ENV = "PROTODATA_VERSION"

    /**
     * An env variable storing a custom [dogfoodingVersion].
     */
    private const val DF_VERSION_ENV = "PROTODATA_DF_VERSION"

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

                ❗ Running an experiment with ProtoData. ❗
                -----------------------------------------
                    Regular version     = v$version
                    Dogfooding version  = v$dogfoodingVersion

                    ProtoData Gradle plugin can now be loaded from Maven Local.

                    To reset the versions, erase the `$$VERSION_ENV` and `$$DF_VERSION_ENV` environment variables.

            """.trimIndent())
        } else {
            version = fallbackVersion
            dogfoodingVersion = fallbackDfVersion
            pluginLib = "$pluginGroup:protodata:$version"
        }
    }
}
