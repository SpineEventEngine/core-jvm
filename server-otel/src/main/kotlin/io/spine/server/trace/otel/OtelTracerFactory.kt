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

@file:OptIn(ExperimentalApi::class)

package io.spine.server.trace.otel

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer as OpenTelemetryTracer
import io.spine.core.Signal
import io.spine.server.ContextSpec
import io.spine.server.trace.Tracer
import io.spine.server.trace.TracerFactory

/**
 * The default instrumentation scope name reported to OpenTelemetry.
 */
public const val DEFAULT_INSTRUMENTATION_SCOPE_NAME: String = "io.spine.server.trace.otel"

/**
 * A [TracerFactory] that records signal handling as OpenTelemetry spans.
 *
 * This factory is backend-agnostic: it maps Spine signals onto OpenTelemetry
 * spans and relies on the supplied [openTelemetry] instance to process and export
 * them. To send traces to a particular backend (an OTLP collector, Google Cloud
 * Trace, Jaeger, etc.), configure the corresponding span processor and exporter on
 * the [OpenTelemetry] instance before passing it here.
 *
 * The [openTelemetry] instance is owned by the caller. Closing this factory does
 * not shut it down.
 *
 * ```
 * val factory = OtelTracerFactory(openTelemetry)
 * ServerEnvironment.instance()
 *     .use(factory)
 * ```
 *
 * @param openTelemetry
 *         the OpenTelemetry instance that processes and exports the recorded spans
 * @param instrumentationScopeName
 *         the instrumentation scope name reported to OpenTelemetry;
 *         defaults to [DEFAULT_INSTRUMENTATION_SCOPE_NAME]
 *
 * @see <a href="https://opentelemetry.io/docs/languages/kotlin/">OpenTelemetry Kotlin</a>
 */
public class OtelTracerFactory @JvmOverloads constructor(
    private val openTelemetry: OpenTelemetry,
    instrumentationScopeName: String = DEFAULT_INSTRUMENTATION_SCOPE_NAME,
) : TracerFactory {

    private val tracer: OpenTelemetryTracer =
        openTelemetry.tracerProvider.getTracer(instrumentationScopeName)

    // A factory is shared across the server environment, so `trace()`, `isOpen()`,
    // and `close()` may run on different threads. The flag is `@Volatile` for safe
    // publication of the closed state.
    @Volatile
    private var open: Boolean = true

    override fun trace(context: ContextSpec, signalMessage: Signal<*, *, *>): Tracer =
        OtelTracer(signalMessage, openTelemetry, tracer, context.name())

    override fun isOpen(): Boolean = open

    override fun close() {
        // The `OpenTelemetry` instance is owned by the caller and is not shut
        // down here. This factory only marks itself as closed.
        open = false
    }
}
