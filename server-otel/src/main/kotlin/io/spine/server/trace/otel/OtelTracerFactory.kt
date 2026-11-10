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

@file:OptIn(ExperimentalApi::class)

package io.spine.server.trace.otel

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer as OpenTelemetryTracer
import io.spine.annotation.Experimental
import io.spine.core.Signal
import io.spine.server.ContextSpec
import io.spine.server.trace.Tracer
import io.spine.server.trace.TracerFactory

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
 * @OptIn(ExperimentalOtelTracing::class)
 * fun tracedEnvironment(openTelemetry: OpenTelemetry): ServerEnvironment =
 *     serverEnvironment {
 *         under<Production> {
 *             use(OtelTracerFactory(openTelemetry))
 *         }
 *     }
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
@ExperimentalOtelTracing
@Experimental
public class OtelTracerFactory @JvmOverloads constructor(
    private val openTelemetry: OpenTelemetry,
    instrumentationScopeName: String = DEFAULT_INSTRUMENTATION_SCOPE_NAME,
) : TracerFactory {

    // Created lazily so that wiring a factory into the server environment does not
    // touch the OpenTelemetry SDK until the first signal is actually traced.
    private val tracer: OpenTelemetryTracer by lazy {
        openTelemetry.tracerProvider.getTracer(instrumentationScopeName)
    }

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

    public companion object {

        /**
         * The default instrumentation scope name reported to OpenTelemetry.
         */
        @ExperimentalOtelTracing
        @Experimental
        public const val DEFAULT_INSTRUMENTATION_SCOPE_NAME: String =
            "io.spine.server.trace.otel"
    }
}
