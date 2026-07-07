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

import com.google.protobuf.ProtocolMessageEnum
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Identifier.newUuid
import io.spine.base.Time.currentTime
import io.spine.core.Event
import io.spine.core.Version
import io.spine.core.Versions
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.Transaction
import io.spine.server.entity.TransactionListener
import io.spine.server.entity.TransactionTest
import io.spine.server.entity.given.tx.Id
import io.spine.server.entity.given.tx.ProjectionState
import io.spine.server.entity.given.tx.ProjectionState.ProjectionType.VERY_USEFUL
import io.spine.server.entity.given.tx.TxProjection
import io.spine.server.entity.given.tx.TxProjection.calculateLength
import io.spine.server.entity.given.tx.TxProjection.typeValueOf
import io.spine.server.entity.given.tx.event.TxCreated
import io.spine.server.type.EventEnvelope
import io.spine.server.type.given.GivenEvent
import io.spine.type.MessageType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private typealias ProjEntity = Projection<Id, ProjectionState, ProjectionState.Builder>
private typealias ProjTx = Transaction<Id, ProjEntity, ProjectionState, ProjectionState.Builder>

/**
 * Tests for [io.spine.server.projection.ProjectionTransaction].
 */
@DisplayName("`ProjectionTransaction` should")
internal class ProjectionTransactionSpec :
    TransactionTest<Id, ProjEntity, ProjectionState, ProjectionState.Builder>() {

    override fun createTx(entity: ProjEntity): ProjTx =
        ProjectionTransaction(entity)

    override fun createTx(entity: ProjEntity, state: ProjectionState, version: Version): ProjTx =
        ProjectionTransaction(entity, state, version)

    override fun createTx(entity: ProjEntity, listener: TransactionListener<Id>): ProjTx {
        val transaction = ProjectionTransaction(entity)
        transaction.setListener(listener)
        return transaction
    }

    override fun createEntity(): ProjEntity = TxProjection(id())

    override fun newState(): ProjectionState {
        val nameString = "The new name for the projection state in this tx"
        return ProjectionState.newBuilder()
            .setId(id())
            .setName(nameString)
            .setNameLength(nameString.length)
            .setType(VERY_USEFUL)
            .build()
    }

    override fun checkEventReceived(entity: ProjEntity, event: Event) {
        val projection = entity as TxProjection
        val actualMessage = unpack(event.message)
        projection.receivedEvents() shouldContain actualMessage
    }

    override fun applyEvent(tx: ProjTx, event: Event): DispatchOutcome {
        val cast = tx as ProjectionTransaction<*, *, *>
        val envelope = EventEnvelope.of(event)
        return cast.play(envelope)
    }

    /**
     * Tests the version advancement strategy for the [Projection]s.
     *
     * The versioning strategy for [Projection] is `VersionIncrement.AutoIncrement`.
     * This test case substitutes [advanceVersionFromEvent], which tested the behavior of
     * the `VersionIncrement.IncrementFromEvent` strategy.
     */
    @Test
    fun `increment version on event`() {
        val entity = createEntity()
        val oldVersion = entity.version()
        val eventVersion = Version.newBuilder()
            .setNumber(42)
            .setTimestamp(currentTime())
            .build()
        val event = GivenEvent.withMessageAndVersion(createEventMessage(), eventVersion)
        Projection.playOn(entity, setOf(event))
        val expected = Versions.increment(oldVersion)

        entity.version().number shouldBe expected.number
        entity.version() shouldNotBe event.context().version
    }

    @Test
    fun `propagate column values to the entity state on commit`() {
        val entity = createEntity() as TxProjection
        val name = "some-projection-name"
        val txCreated = TxCreated.newBuilder()
            .setId(id())
            .setName(name)
            .build()
        val event = GivenEvent.withMessage(txCreated)
        Projection.playOn(entity, setOf(event))

        entity.state().nameLength shouldBe calculateLength(name)
        entity.state().type shouldBe typeValueOf(false)
    }

    @Test
    fun `propagate 'null' column values as default values for the field`() {
        val id = Id.newBuilder()
            .setId(newUuid())
            .build()
        val entity = TxProjection(id, true)

        val name = newUuid()
        val txCreated = TxCreated.newBuilder()
            .setId(id())
            .setName(name)
            .build()
        val event = GivenEvent.withMessage(txCreated)
        Projection.playOn(entity, setOf(event))

        val state = entity.state()
        val messageType = MessageType(state.descriptorForType)
        val field = messageType.field("type")
        val actualType = state.type

        val actualTypeDescriptor = (actualType as ProtocolMessageEnum).valueDescriptor
        val defaultTypeDescriptor = field.descriptor().defaultValue
        actualTypeDescriptor shouldBe defaultTypeDescriptor

        // Make sure the transaction wasn't rolled back.
        entity.version().number shouldBe 1
    }
}
