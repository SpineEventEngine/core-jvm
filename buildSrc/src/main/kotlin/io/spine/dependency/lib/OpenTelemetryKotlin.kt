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
 * @see <a href="https://github.com/open-telemetry/opentelemetry-kotlin">opentelemetry-kotlin</a>
 * @see <a href="https://opentelemetry.io/docs/languages/kotlin/">Kotlin docs</a>
 */
@Suppress("unused")
object OpenTelemetryKotlin : Dependency() {

    override val version = "0.4.0"
    override val group = "io.opentelemetry.kotlin"

    /**
     * The Kotlin Multiplatform API surface (traces, metrics, logs).
     *
     * This is the only artifact required by production code that maps
     * a domain onto OpenTelemetry spans.
     */
    val api = "$group:api:$version"

    /**
     * A no-op implementation of the [api].
     */
    val noop = "$group:noop:$version"

    /**
     * The Kotlin SDK API: span processors, exporters, samplers, and the
     * configuration DSL.
     *
     * This artifact exposes the SDK types on the compile classpath; the
     * `createOpenTelemetry { }` entry point that instantiates them lives in
     * [implementation]. Applications and tests configure an `OpenTelemetry`
     * instance using these two artifacts; the production code needs only the [api].
     */
    val core = "$group:core:$version"

    /**
     * The Kotlin SDK implementation, including the `createOpenTelemetry { }`
     * entry point. Use together with [core].
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
