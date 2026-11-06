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

package io.spine.server.event

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.core.Ack
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Event
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.system.server.DiagnosticMonitor
import io.spine.test.event.ProjectCreated
import io.spine.test.event.projectCreated
import io.spine.test.event.projectId
import io.spine.testing.server.TestEventFactory
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `EventBusTest` with the specs of the events that the bus refuses.
 */
@DisplayName("`EventBus` should")
internal class EventBusSpec {

    private val events = TestEventFactory.newInstance(EventBusSpec::class.java)

    private val monitor = DiagnosticMonitor()
    private lateinit var context: BoundedContext

    @BeforeEach
    fun createContext() {
        context = BoundedContextBuilder.assumingTests().build()
        context.internalAccess().registerEventDispatcher(monitor)
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    fun `refuse an invalid event and record the violations`() {
        val invalid = projectCreatedEvent().toBuilder()
            .clearId()
            .buildPartial()

        post(invalid).status.errorCase shouldBe INVALID_REQUEST

        val failures = monitor.eventValidationFailures()
        failures shouldHaveSize 1
        val failure = failures.single()
        failure.id shouldBe invalid.id
        failure.validationError.constraintViolationList.shouldNotBeEmpty()
    }

    @Test
    fun `refuse an event from another context that nothing handles, and record it`() {
        val domestic = projectCreatedEvent()
        val external = domestic.toBuilder()
            .setContext(domestic.context.toBuilder().setExternal(true))
            .build()

        post(external).status.errorCase shouldBe UNSUPPORTED_REQUEST

        val received = monitor.unsupportedEvents()
        received shouldHaveSize 1
        val unsupported = received.single()
        unsupported.id shouldBe external.id
        unsupported.typeUrl shouldBe TypeUrl.of(ProjectCreated::class.java).value()
    }

    @Test
    fun `refuse a domestic event that nothing handles, and record nothing`() {
        post(projectCreatedEvent()).status.errorCase shouldBe UNSUPPORTED_REQUEST

        monitor.unsupportedEvents().shouldBeEmpty()
    }

    private fun projectCreatedEvent(): Event =
        events.createEvent(projectCreated {
            projectId = projectId { id = newUuid() }
        })

    private fun post(event: Event): Ack {
        val observer = memoizingObserver<Ack>()
        context.eventBus().post(event, observer)
        return observer.firstResponse()
    }
}
