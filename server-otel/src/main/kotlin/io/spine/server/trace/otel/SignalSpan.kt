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
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.Tracer as OpenTelemetryTracer
import io.spine.base.Time
import io.spine.code.java.ClassName
import io.spine.core.BoundedContextName
import io.spine.core.MessageId
import io.spine.core.Signal
import io.spine.core.SignalId
import io.spine.protobuf.AnyPacker.unpack
import io.spine.system.server.EntityTypeName
import io.spine.time.toNanos

/**
 * The maximum length of a span display name, in characters.
 */
private const val DISPLAY_NAME_MAX_LENGTH = 128

/**
 * The hex representation of the "sampled" trace flags.
 *
 * The synthetic parent span context is always marked as sampled, so that a
 * parent-based sampler records and exports the emitted handler spans. This
 * matches the always-export behavior of the retired Stackdriver tracer; a caller
 * may still apply its own sampling via the configured `OpenTelemetry` SDK.
 */
private const val SAMPLED_TRACE_FLAGS = "01"

/**
 * A single handling of a [signal] by an entity, recorded as an OpenTelemetry span.
 *
 * A signal may be handled once or many times by different entities. Each handling
 * is recorded as its own span — deliberately, not as a span event on a shared span:
 * OpenTelemetry deprecated the Span Events API in March 2026 in favor of log-based
 * events, so per-handling data is carried by dedicated spans and their attributes.
 * Spans produced for signals that descend from a common root signal share a single
 * [trace][traceIdOf], so that a whole causal chain appears as one trace.
 *
 * @see <a href="https://opentelemetry.io/blog/2026/deprecating-span-events/">Deprecating Span Events</a>
 */
internal class SignalSpan(
    val contextName: BoundedContextName,
    val signal: Signal<*, *, *>,
    val receiver: MessageId,
    val receiverType: EntityTypeName,
) {

    /**
     * Records this signal handling as a span produced by the given [tracer].
     *
     * The span covers the interval from the signal's [timestamp][Signal.timestamp]
     * to the current time, since the handling has already completed by the time
     * this method is called. Exporting the span is the responsibility of the
     * span processor configured in [otel].
     */
    fun recordWith(otel: OpenTelemetry, tracer: OpenTelemetryTracer) {
        val span = tracer.startSpan(
            name = displayName(),
            parentContext = parentContext(otel),
            startTimestamp = signal.timestamp().toNanos(),
        ) {
            SpanAttribute.entries.forEach { attribute ->
                setStringAttribute(attribute.key, attribute.truncatedValueIn(this@SignalSpan))
            }
        }
        span.end(Time.currentTime().toNanos())
    }

    /**
     * Builds the span display name in the form `"<Receiver> handles <Signal>"`.
     */
    private fun displayName(): String {
        val receiverName = ClassName.of(receiverType.javaClassName).toSimple()
        val signalName = signal.enclosedTypeUrl().typeName().simpleName()
        return "$receiverName handles $signalName".truncated(DISPLAY_NAME_MAX_LENGTH)
    }

    /**
     * Builds the parent context that carries the deterministic trace ID of the
     * causal chain this signal belongs to.
     */
    private fun parentContext(otel: OpenTelemetry): Context {
        val rootId = rootSignalId()
        val parent = otel.spanContext.create(
            traceId = traceIdOf(rootId),
            spanId = spanIdOf(rootId),
            traceFlags = otel.traceFlags.fromHex(SAMPLED_TRACE_FLAGS),
            traceState = otel.traceState.default,
            isRemote = false,
        )
        return otel.context.root().storeSpan(otel.span.fromSpanContext(parent))
    }

    /**
     * Obtains the ID of the root signal of the causal chain this signal belongs to.
     *
     * The ID is unpacked as a concrete message (e.g. a `CommandId` or `EventId`),
     * since [SignalId] is an interface and cannot be instantiated directly.
     */
    private fun rootSignalId(): SignalId =
        unpack(signal.rootMessage().id) as SignalId
}
