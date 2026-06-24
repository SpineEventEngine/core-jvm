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

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.opentelemetry.kotlin.ExperimentalApi
import io.spine.base.CommandMessage
import io.spine.environment.Tests
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.ServerEnvironment
import io.spine.server.trace.given.TracingTestEnv.scheduleFlight
import io.spine.server.trace.given.airport.AirportContext
import io.spine.server.trace.otel.given.RecordingSpanProcessor
import io.spine.server.trace.otel.given.recordingOpenTelemetry
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`OtelTracerFactory`, wired into a bounded context, should")
internal class OtelTracingSpec {

    private val requests = TestActorRequestFactory(OtelTracingSpec::class.java)

    private lateinit var spans: RecordingSpanProcessor
    private lateinit var context: BoundedContext

    @BeforeEach
    fun setUp() {
        spans = RecordingSpanProcessor()
        val factory = OtelTracerFactory(recordingOpenTelemetry(spans))
        ServerEnvironment.`when`(Tests::class.java)
            .use(factory)
        context = AirportContext.builder().build()
    }

    @AfterEach
    fun tearDown() {
        ServerEnvironment.instance().reset()
    }

    @Test
    fun `record a span when a signal is handled`() {
        post(scheduleFlight())

        spans.spans.shouldNotBeEmpty()
        spans.spans.map { it.name } shouldContain "FlightAggregate handles ScheduleFlight"
    }

    @Test
    fun `group all spans of one causal chain under a single trace`() {
        post(scheduleFlight())

        val traceIds = spans.spans.map { it.spanContext.traceId }.distinct()
        traceIds shouldHaveSize 1
    }

    private fun post(command: CommandMessage) {
        val cmd = requests.command().create(command)
        context.commandBus().post(cmd, noOpObserver())
    }
}
