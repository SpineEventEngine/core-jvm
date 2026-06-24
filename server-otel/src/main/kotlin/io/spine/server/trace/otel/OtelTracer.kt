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
import io.spine.annotation.Experimental
import io.spine.core.BoundedContextName
import io.spine.core.MessageId
import io.spine.core.Signal
import io.spine.server.trace.AbstractTracer
import io.spine.system.server.EntityTypeName

/**
 * A [Tracer][io.spine.server.trace.Tracer] that records the handling of a signal
 * as OpenTelemetry spans.
 *
 * Each invocation of [processedBy] starts and immediately ends a span describing
 * a single handling of the [signal][signal] by an entity. The span carries the
 * signal's [timestamp][Signal.timestamp] as its start time and the current time
 * as its end time.
 *
 * Instances are created by [OtelTracerFactory] and are not meant to be reused
 * across signals.
 */
@ExperimentalOtelTracing
@Experimental
public class OtelTracer internal constructor(
    signal: Signal<*, *, *>,
    private val openTelemetry: OpenTelemetry,
    private val tracer: OpenTelemetryTracer,
    private val contextName: BoundedContextName,
) : AbstractTracer(signal) {

    override fun processedBy(receiver: MessageId, receiverType: EntityTypeName) {
        val signalSpan = SignalSpan(contextName, signal(), receiver, receiverType)
        signalSpan.recordWith(openTelemetry, tracer)
    }

    override fun close() {
        // Each span is started and ended within `processedBy`, so there is
        // nothing to flush here. Exporting the recorded spans is the
        // responsibility of the `OpenTelemetry` instance configured by the caller.
    }
}
