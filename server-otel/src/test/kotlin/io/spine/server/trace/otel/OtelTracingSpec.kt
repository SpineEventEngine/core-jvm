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

@file:OptIn(ExperimentalApi::class, ExperimentalOtelTracing::class)

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
import io.spine.server.under
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
        under<Tests> {
            use(factory)
        }
        context = AirportContext.builder().build()
    }

    @AfterEach
    fun tearDown() {
        context.close()
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
