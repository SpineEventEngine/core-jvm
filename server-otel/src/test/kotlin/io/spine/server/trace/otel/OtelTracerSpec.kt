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

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.opentelemetry.kotlin.ExperimentalApi
import io.spine.base.Identifier
import io.spine.core.Command
import io.spine.core.Versions.zero
import io.spine.core.messageId
import io.spine.server.ContextSpec.singleTenant
import io.spine.server.trace.given.TracingTestEnv.FLIGHT
import io.spine.server.trace.given.TracingTestEnv.FLIGHT_TYPE
import io.spine.server.trace.given.TracingTestEnv.scheduleFlight
import io.spine.server.trace.otel.given.RecordingSpanProcessor
import io.spine.server.trace.otel.given.recordingOpenTelemetry
import io.spine.string.Stringifiers
import io.spine.system.server.EntityTypeName
import io.spine.system.server.entityTypeName
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`OtelTracer` should")
internal class OtelTracerSpec {

    private val requests = TestActorRequestFactory(OtelTracerSpec::class.java)
    private val spec = singleTenant(OtelTracerSpec::class.java.name)

    private lateinit var spans: RecordingSpanProcessor
    private lateinit var factory: OtelTracerFactory
    private lateinit var command: Command

    @BeforeEach
    fun setUp() {
        spans = RecordingSpanProcessor()
        factory = OtelTracerFactory(recordingOpenTelemetry(spans))
        command = requests.command().create(scheduleFlight())
    }

    @Test
    fun `record a span for each handling of a signal`() {
        trace(command).use {
            it.processedBy(flightReceiver(), flightType())
        }
        spans.spans shouldHaveSize 1
    }

    @Test
    fun `report the configured instrumentation scope`() {
        val scope = "custom.tracer.scope"
        OtelTracerFactory(recordingOpenTelemetry(spans), scope)
            .trace(spec, command)
            .use { it.processedBy(flightReceiver(), flightType()) }
        span().instrumentationScopeInfo.name shouldBe scope
    }

    @Nested
    @DisplayName("populate the recorded span with")
    inner class SpanContent {

        @BeforeEach
        fun record() {
            trace(command).use {
                it.processedBy(flightReceiver(), flightType())
            }
        }

        @Test
        fun `the display name`() {
            span().name shouldBe "FlightAggregate handles ScheduleFlight"
        }

        @Test
        fun `the signal timestamp as the start time`() {
            val timestamp = command.timestamp()
            span().startTimestamp shouldBe timestamp.seconds * NANOS_PER_SECOND + timestamp.nanos
        }

        @Test
        fun `a non-null end time at or after the start`() {
            val span = span()
            val end = span.endTimestamp
            (end != null && end >= span.startTimestamp) shouldBe true
        }

        @Test
        fun `the trace ID derived from the root signal`() {
            span().spanContext.traceId shouldBe traceIdOf(command.id())
        }

        @Test
        fun `the synthetic parent span ID`() {
            span().parent.spanId shouldBe spanIdOf(command.id())
        }

        @Test
        fun `the Spine attributes`() {
            val attributes = span().attributes
            attributes["spine.bounded_context"] shouldBe spec.name().value
            attributes["spine.signal.id"] shouldBe command.id().value()
            attributes["spine.signal.type"] shouldBe command.enclosedTypeUrl().value()
            attributes["spine.entity.id"] shouldBe Stringifiers.toString(FLIGHT)
            attributes["spine.entity.type"] shouldBe FLIGHT_TYPE.value()
            attributes shouldContainKey "spine.tenant"
        }
    }

    @Test
    fun `group spans from the same signal under one trace`() {
        trace(command).use {
            it.processedBy(flightReceiver(), flightType())
            it.processedBy(flightReceiver(), flightType())
        }
        val traceIds = spans.spans.map { it.spanContext.traceId }.distinct()
        traceIds shouldHaveSize 1
        traceIds.single() shouldBe traceIdOf(command.id())
    }

    private fun trace(command: Command) = factory.trace(spec, command)

    private fun span() = spans.spans.single()

    private fun flightReceiver() = messageId {
        id = Identifier.pack(FLIGHT)
        typeUrl = FLIGHT_TYPE.value()
        version = zero()
    }

    private fun flightType(): EntityTypeName = entityTypeName {
        javaClassName = FLIGHT_AGGREGATE_CLASS
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val FLIGHT_AGGREGATE_CLASS = "io.spine.server.trace.given.airport.FlightAggregate"
    }
}
