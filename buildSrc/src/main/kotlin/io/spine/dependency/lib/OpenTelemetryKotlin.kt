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

package io.spine.dependency.lib

import io.spine.dependency.Dependency

/**
 * The Kotlin Multiplatform API and SDK for OpenTelemetry.
 *
 * The project is currently in the alpha stage, and its API is marked
 * with `@io.opentelemetry.kotlin.ExperimentalApi`. Modules using these artifacts
 * must opt in to that marker.
 *
 * The project does not publish a BOM, so the version is embedded in each
 * artifact coordinate below.
 *
 * @see <a href="https://github.com/open-telemetry/opentelemetry-kotlin">GitHub project</a>
 * @see <a href="https://opentelemetry.io/docs/languages/kotlin/">Kotlin documentation</a>
 */
@Suppress("unused")
object OpenTelemetryKotlin : Dependency() {

    // https://central.sonatype.com/artifact/io.opentelemetry.kotlin/api
    //
    // The project is in the alpha stage, so bump this version only together
    // with the code that consumes it.
    override val version = "0.4.0"
    override val group = "io.opentelemetry.kotlin"

    /**
     * The Kotlin Multiplatform API surface (traces, metrics, logs).
     *
     * This is the artifact production code compiles against; the SDK artifacts
     * below are for applications and tests.
     */
    val api = "$group:api:$version"

    /**
     * A no-op implementation of the [api] artifact.
     */
    val noop = "$group:noop:$version"

    /**
     * The Kotlin SDK API: span processors, exporters, samplers, and the
     * configuration DSL.
     *
     * This artifact exposes the SDK types on the compile classpath, while the
     * `createOpenTelemetry { }` entry point that instantiates them lives in
     * [implementation]. Applications and tests configure an `OpenTelemetry`
     * instance using these two artifacts; production code needs only
     * the [api] artifact.
     */
    val core = "$group:core:$version"

    /**
     * The Kotlin SDK implementation, including the `createOpenTelemetry { }`
     * entry point.
     *
     * Use it together with [core].
     */
    val implementation = "$group:implementation:$version"

    /**
     * The compatibility bridge to the OpenTelemetry Java SDK.
     */
    val compat = "$group:compat:$version"

    override val modules: List<String> = listOf(
        "$group:api",
        "$group:noop",
        "$group:core",
        "$group:implementation",
        "$group:compat",
    )
}
