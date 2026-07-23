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

package io.spine.server.projection

import com.google.common.collect.ImmutableList
import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier
import io.spine.base.Identifier.newUuid
import io.spine.base.Time.currentTime
import io.spine.core.EventValidationError
import io.spine.core.EventValidationError.UNSUPPORTED_EVENT_VALUE
import io.spine.core.Versions
import io.spine.core.eventId
import io.spine.core.messageId
import io.spine.protobuf.AnyPacker.pack
import io.spine.server.BoundedContextBuilder
import io.spine.server.DefaultRepository
import io.spine.server.entity.given.Given
import io.spine.server.projection.given.EntitySubscriberProjection
import io.spine.server.projection.given.NoDefaultOptionProjection
import io.spine.server.projection.given.NoDefaultOptionProjection.ACCEPTED_VALUE
import io.spine.server.projection.given.SavedString
import io.spine.server.projection.given.SavingProjection
import io.spine.server.projection.given.dispatch.ProjectionEventDispatcher.dispatch
import io.spine.server.type.EventEnvelope
import io.spine.server.type.given.GivenEvent
import io.spine.server.type.given.GivenEvent.withMessage
import io.spine.system.server.DiagnosticMonitor
import io.spine.system.server.event.entityStateChanged
import io.spine.test.projection.Project
import io.spine.test.projection.ProjectId
import io.spine.test.projection.project
import io.spine.test.projection.projectId
import io.spine.test.projection.projectTaskNames
import io.spine.test.projection.task
import io.spine.test.projection.taskId
import io.spine.test.projection.event.int32Imported
import io.spine.test.projection.event.stringImported
import io.spine.testing.TestValues.random
import io.spine.testing.TestValues.randomString
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Projection` should")
internal class ProjectionSpec {

    private val eventFactory = TestEventFactory.newInstance(ProjectionSpec::class.java)

    private lateinit var projection: SavingProjection

    @BeforeEach
    fun setUp() {
        val id = newUuid()
        projection = Given.projectionOfClass(SavingProjection::class.java)
            .withId(id)
            .withVersion(1)
            .withState(
                SavedString.newBuilder()
                    .setId(id)
                    .setValue("Initial state")
                    .build()
            )
            .build()
    }

    @Test
    fun `handle events`() {
        val stringEvent = stringImported { value = newUuid() }
        val strEvt = eventFactory.createEvent(stringEvent)
        dispatch(projection, stringEvent, strEvt.context())
        projection.changed() shouldBe true
        projection.state().value shouldContain stringEvent.value

        val integerEvent = int32Imported { value = 42 }
        val intEvt = eventFactory.createEvent(integerEvent)
        dispatch(projection, integerEvent, intEvt.context())
        projection.changed() shouldBe true
        projection.state().value shouldContain integerEvent.value.toString()
    }

    @Test
    fun `receive entity state updates`() {
        val projId = newId()
        val newTask = task {
            taskId = taskId { id = random(1, 1_000) }
            title = "test task " + random(42)
        }
        val projectName = "test project name " + randomString()
        val aggregateState = project {
            id = projId
            name = projectName
            status = Project.Status.STARTED
            task.add(newTask)
        }
        val previousAggState = aggregateState.toBuilder()
            .setName("Old $projectName")
            .build()
        val entityId = messageId {
            typeUrl = aggregateState.typeUrl().value()
            id = pack(projId)
            version = Versions.zero()
        }
        val causeId = eventId { value = newUuid() }
        val systemEvent = entityStateChanged {
            entity = entityId
            oldState = pack(previousAggState)
            newState = pack(aggregateState)
            when_ = currentTime()
            signalId.add(
                messageId {
                    id = pack(causeId)
                    typeUrl = "example.org/example.test.Event"
                }
            )
        }
        val projection = EntitySubscriberProjection(projId)
        dispatch(projection, withMessage(systemEvent))
        projection.state() shouldBe projectTaskNames {
            projectId = projId
            this.projectName = projectName
            taskName.add(newTask.title)
        }
    }

    @Test
    fun `dispatch unexpected handler failure system rejection with 'UNSUPPORTED_EVENT' cause`() {
        @Suppress("UNCHECKED_CAST")
        val repository = DefaultRepository.of(SavingProjection::class.java)
                as ProjectionRepository<String, SavingProjection, SavedString>
        val context = BoundedContextBuilder.assumingTests().build()
        val contextAccess = context.internalAccess()
        contextAccess.register(repository)
        val monitor = DiagnosticMonitor()
        contextAccess.registerEventDispatcher(monitor)
        val event = GivenEvent.arbitrary()
        val envelope = EventEnvelope.of(event)
        val endpoint = ProjectionEndpoint.of<String, SavingProjection, SavedString>(
            repository,
            envelope
        )

        endpoint.dispatchTo(projection.id)

        val systemEvents = monitor.handlerFailureEvents()
        systemEvents shouldHaveSize 1
        val systemEvent = systemEvents[0]
        systemEvent.handledSignal.asEventId() shouldBe event.id()
        systemEvent.entity.id shouldBe Identifier.pack(projection.id)
        val error = systemEvent.error
        error.type shouldBe EventValidationError.getDescriptor().fullName
        error.code shouldBe UNSUPPORTED_EVENT_VALUE
    }

    @Test
    fun `expose 'play events' operation to package`() {
        val stringImported = stringImported { value = "eins zwei drei" }
        val integerImported = int32Imported { value = 123 }
        val nextVersion = Versions.increment(projection.version())
        val e1 = eventFactory.createEvent(stringImported, nextVersion)
        val e2 = eventFactory.createEvent(integerImported, Versions.increment(nextVersion))

        val projectionChanged = Projection.playOn(projection, ImmutableList.of(e1, e2))
        projectionChanged shouldBe true

        val projectionState = projection.state().value
        projectionState shouldContain stringImported.value
        projectionState shouldContain integerImported.value.toString()
    }

    @Test
    fun `not dispatch event if it does not match filters`() {
        val projection = Given.projectionOfClass(NoDefaultOptionProjection::class.java)
            .withId(newUuid())
            .build()
        val skipped = stringImported { value = "BBB" }
        dispatch(projection, eventFactory.createEvent(skipped))
        assertThat(projection.state())
            // Ignore the difference in the ID field of the state that
            // was set automatically by the tx.
            .comparingExpectedFieldsOnly()
            .isEqualTo(SavedString.getDefaultInstance())

        val dispatched = stringImported { value = ACCEPTED_VALUE }
        dispatch(projection, eventFactory.createEvent(dispatched))
        projection.state().value shouldBe ACCEPTED_VALUE
    }

    private fun newId(): ProjectId = projectId { id = newUuid() }
}
