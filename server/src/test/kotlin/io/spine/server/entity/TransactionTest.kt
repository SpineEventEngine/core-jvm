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

package io.spine.server.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.EntityState
import io.spine.base.EventMessage
import io.spine.base.Errors.fromThrowable
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.Event
import io.spine.core.Version
import io.spine.core.Versions
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.given.MemoizingTransactionListener
import io.spine.server.entity.given.tx.Id
import io.spine.server.entity.given.tx.event.TxCreated
import io.spine.server.entity.given.tx.event.TxErrorRequested
import io.spine.server.entity.given.tx.event.TxStateErrorRequested
import io.spine.server.type.given.GivenEvent.withMessage
import io.spine.server.type.given.GivenEvent.withMessageAndVersion
import io.spine.testing.TestValues.randomString
import io.spine.testing.server.model.ModelTests
import io.spine.validation.ValidatingBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Base class for testing the [transactions][Transaction] for different
 * [TransactionalEntity] implementations.
 *
 * The test cases are declared as flat `@Test` methods rather than inside `@Nested`
 * classes on purpose: JUnit discovers `@Nested` classes only when they are declared
 * in the concrete test class, not when inherited from a superclass, whereas inherited
 * `@Test` methods are executed for every subclass.
 */
abstract class TransactionTest<I : Any,
                               E : TransactionalEntity<I, S, B>,
                               S : EntityState<I>,
                               B : ValidatingBuilder<S>> {

    /**
     * Creates the instance of the ID with the simple name of test suite class.
     */
    protected fun id(): Id =
        Id.newBuilder()
            .setId(javaClass.simpleName + '-' + Identifier.newUuid())
            .build()

    protected abstract fun createTx(entity: E): Transaction<I, E, S, B>

    protected abstract fun createTx(entity: E, state: S, version: Version): Transaction<I, E, S, B>

    protected abstract fun createTx(
        entity: E,
        listener: TransactionListener<I>
    ): Transaction<I, E, S, B>

    protected abstract fun createEntity(): E

    protected abstract fun newState(): S

    protected abstract fun checkEventReceived(entity: E, event: Event)

    /**
     * Creates an event message that would be normally handled by the entity.
     */
    protected fun createEventMessage(): EventMessage =
        TxCreated.newBuilder()
            .setId(id())
            .setName("Name " + randomString())
            .build()

    /**
     * Creates an event message handling of which causes an exception in the entity
     * served by the transaction under the test.
     */
    protected fun failingInHandler(): EventMessage =
        TxErrorRequested.newBuilder()
            .setId(id())
            .build()

    /**
     * Creates an event message handling of which turns the builder of the entity state
     * into the state that fails the validation.
     */
    protected fun failingStateTransition(): EventMessage =
        TxStateErrorRequested.newBuilder()
            .setId(id())
            .build()

    protected abstract fun applyEvent(tx: Transaction<I, E, S, B>, event: Event): DispatchOutcome

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
    }

    @Test
    fun `initialize a transaction from an entity`() {
        val entity = createEntity()
        val expectedBuilder = Transaction.toBuilder(entity)
        val expectedVersion = entity.version()
        val expectedLifecycleFlags = entity.lifecycleFlags()

        val tx = createTx(entity)

        tx.entity shouldBe entity
        // Not possible to compare `Message.Builder` instances via `equals`, so trigger `build()`.
        tx.builder.buildPartial() shouldBe expectedBuilder.buildPartial()
        tx.version shouldBe expectedVersion
        tx.lifecycleFlags shouldBe expectedLifecycleFlags
        tx.isActive shouldBe true
        tx.phases().shouldBeEmpty()
    }

    @Test
    fun `initialize a transaction from an entity, state, and version`() {
        val entity = createEntity()
        val state = newState()
        val version = newVersion()

        state shouldNotBe entity.state()
        version shouldNotBe entity.version()

        val tx = createTx(entity, state, version)

        tx.builder.build() shouldBe state
        tx.version shouldBe version
        state shouldNotBe entity.state()
        version shouldNotBe entity.version()

        tx.commit()

        // Now test that the state and the version of the entity changed to that from the tx.
        entity.state() shouldBe state
        entity.version() shouldBe version
    }

    @Test
    fun `deliver events to handler methods`() {
        val entity = createEntity()
        val tx = createTx(entity)

        val event = withMessage(createEventMessage())
        applyEvent(tx, event)

        checkEventReceived(entity, event)
    }

    @Test
    fun `create phase for each dispatched message`() {
        val entity = createEntity()
        val tx = createTx(entity)

        val event = withMessage(createEventMessage())
        val outcome = applyEvent(tx, event)
        outcome.hasSuccess() shouldBe true
        tx.phases() shouldHaveSize 1

        val phase = tx.phases()[0]

        phase.messageId() shouldBe event.id()
    }

    @Test
    fun `propagate changes to entity when phase is propagated`() {
        val entity = createEntity()

        val stateBeforePhase = entity.state()
        val versionBeforePhase = entity.version()

        val tx = createTx(entity)
        val event = withMessage(createEventMessage())

        applyEvent(tx, event)

        val modifiedState = entity.state()
        val modifiedVersion = entity.version()

        modifiedState shouldNotBe stateBeforePhase
        modifiedVersion shouldNotBe versionBeforePhase
    }

    @Test
    fun `not propagate changes to entity on rollback`() {
        val entity = createEntity()
        val stateBeforeRollback = entity.state()
        val versionBeforeRollback = entity.version()

        val tx = createTx(entity)

        val event = withMessage(createEventMessage())
        val outcome = applyEvent(tx, event)
        outcome.hasSuccess() shouldBe true
        val exception = RuntimeException("that triggers rollback")
        tx.rollback(fromThrowable(exception))

        val stateAfterRollback = entity.state()
        val versionAfterRollback = entity.version()

        stateAfterRollback shouldBe stateBeforeRollback
        versionAfterRollback shouldBe versionBeforeRollback
    }

    @Test
    fun `set transaction entity version from event context`() {
        val entity = createEntity()

        val tx = createTx(entity)
        val event = withMessage(createEventMessage())

        val ctxVersion = event.context().version
        ctxVersion shouldNotBe tx.version

        applyEvent(tx, event)
        val modifiedVersion = tx.version
        tx.version shouldBe modifiedVersion
    }

    @Test
    fun `notify listener during transaction execution`() {
        val listener = MemoizingTransactionListener<I>()
        val entity = createEntity()
        val tx = createTx(entity, listener)
        val event = withMessage(createEventMessage())

        val outcome = applyEvent(tx, event)
        outcome.hasSuccess() shouldBe true

        val phases = listener.phasesOnAfter()
        phases shouldHaveSize 1
        val phase = phases[0]
        phase.messageId() shouldBe event.getId()
    }

    @Test
    fun `not allow injecting state if entity has non-zero version already`() {
        val entity = createEntity()
        entity.incrementVersion()
        val state = newState()
        val version = newVersion()

        shouldThrow<IllegalStateException> {
            createTx(entity, state, version)
        }
    }

    @Test
    fun `throw 'IllegalStateException' on phase failure`() {
        val entity = createEntity()

        val tx = createTx(entity)

        val event = withMessage(failingInHandler())

        val outcome = applyEvent(tx, event)
        outcome.hasError() shouldBe true
    }

    @Test
    fun `throw 'InvalidEntityStateException' on state transition failure`() {
        val entity = createEntity()
        val tx = createTx(entity)
        val event = withMessage(failingStateTransition())

        val outcome = applyEvent(tx, event)
        outcome.hasError() shouldBe true
    }

    @Test
    fun `rollback automatically on violation in message handler`() {
        val entity = createEntity()
        val originalState = entity.state()
        val originalVersion = entity.version()

        val tx = createTx(entity)

        val event = withMessage(failingInHandler())
        val outcome = applyEvent(tx, event)
        outcome.hasError() shouldBe true
        checkRollback(entity, originalState, originalVersion)
    }

    @Test
    fun `rollback automatically on violation at state transition`() {
        val entity = createAndModify()
        val originalState = entity.state()
        val originalVersion = entity.version()

        val tx = createTx(entity)
        val nextVersion = Versions.increment(entity.version())
        val event = withMessageAndVersion(failingStateTransition(), nextVersion)

        val outcome = applyEvent(tx, event)
        outcome.hasError() shouldBe true
        checkRollback(entity, originalState, originalVersion)
    }

    @Test
    fun `init builder with the entity ID, if the field is required or assumed required`() {
        val entity = createEntity()
        val tx = createTx(entity)
        val firstField =
            entity.state()
                .descriptorForType
                .fields[0]
        tx.builder.getField(firstField) shouldBe entity.id()
    }

    /**
     * Call this method in derived transaction tests if corresponding transaction
     * carries version number into an entity.
     *
     * @implNote This method uses the module-internal API of the [Transaction] class.
     */
    protected fun advanceVersionFromEvent() {
        val entity = createEntity()
        val tx = createTx(entity)
        tx.version shouldBe entity.version()

        val event = withMessage(createEventMessage())
        applyEvent(tx, event)
        val versionFromEvent = event.context().version
        tx.version shouldBe versionFromEvent
        tx.commit()
        entity.version() shouldBe versionFromEvent
    }

    private fun createAndModify(): E {
        val entity = createEntity()
        val tx = createTx(entity)
        val event = withMessage(createEventMessage())
        applyEvent(tx, event)
        tx.commit()
        return entity
    }

    private fun checkRollback(entity: E, originalState: S, originalVersion: Version) {
        entity.transaction().shouldBeNull()
        entity.state() shouldBe originalState
        entity.version() shouldBe originalVersion
    }

    private companion object {

        fun newVersion(): Version = Versions.newVersion(42, currentTime())
    }
}
