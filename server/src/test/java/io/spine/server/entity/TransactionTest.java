/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
package io.spine.server.entity;

import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.given.MemoizingTransactionListener;
import io.spine.server.entity.given.tx.Id;
import io.spine.server.entity.given.tx.event.TxCreated;
import io.spine.server.entity.given.tx.event.TxErrorRequested;
import io.spine.server.entity.given.tx.event.TxStateErrorRequested;
import io.spine.testing.server.model.ModelTests;
import io.spine.validation.ValidatingBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Errors.fromThrowable;
import static io.spine.base.Time.currentTime;
import static io.spine.server.entity.Transaction.toBuilder;
import static io.spine.server.type.given.GivenEvent.withMessage;
import static io.spine.server.type.given.GivenEvent.withMessageAndVersion;
import static io.spine.testing.TestValues.randomString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class for testing the {@linkplain Transaction transactions} for different
 * {@link TransactionalEntity} implementations.
 */
public abstract class TransactionTest<I,
        E extends TransactionalEntity<I, S, B>,
        S extends EntityState<I>,
        B extends ValidatingBuilder<S>> {

    /**
     * Creates the instance of the ID with the simple name of test suite class.
     */
    protected final Id id() {
        return Id.newBuilder()
                 .setId(getClass().getSimpleName() + '-' + Identifier.newUuid())
                 .build();
    }

    private static Version newVersion() {
        return Versions.newVersion(42, currentTime());
    }

    protected abstract Transaction<I, E, S, B> createTx(E entity);

    protected abstract Transaction<I, E, S, B> createTx(E entity, S state, Version version);

    protected abstract
    Transaction<I, E, S, B> createTx(E entity, TransactionListener<I> listener);

    protected abstract E createEntity();

    protected abstract S newState();

    protected abstract void checkEventReceived(E entity, Event event);

    /**
     * Creates an event message which would be normally handled by the entity.
     */
    protected final EventMessage createEventMessage() {
        return TxCreated.newBuilder()
                        .setId(id())
                        .setName("Name " + randomString())
                        .build();
    }

    /**
     * Creates an event message handling of which causes an exception in the entity
     * served by the transaction under the test.
     */
    protected final EventMessage failingInHandler() {
        return TxErrorRequested
                .newBuilder()
                .setId(id())
                .build();
    }

    /**
     * Creates an event message handling of which turns the builder of the entity state
     * into the state which fails the validation.
     */
    protected final EventMessage failingStateTransition() {
        return TxStateErrorRequested
                .newBuilder()
                .setId(id())
                .build();
    }

    protected abstract DispatchOutcome applyEvent(Transaction<I, E, S, B> tx, Event event);

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
    }

    @Nested
    @DisplayName("initialize")
    class Initialize {

        @Test
        @DisplayName("from entity")
        void fromEntity() {
            var entity = createEntity();
            var expectedBuilder = toBuilder(entity);
            var expectedVersion = entity.version();
            var expectedLifecycleFlags = entity.lifecycleFlags();

            var tx = createTx(entity);
            assertNotNull(tx);

            assertThat(tx.entity())
                    .isEqualTo(entity);
            // Not possible to compare `Message.Builder` instances via `equals`, so trigger `build()`.
            assertThat(tx.builder().buildPartial())
                    .isEqualTo(expectedBuilder.buildPartial());
            assertThat(tx.version())
                    .isEqualTo(expectedVersion);
            assertThat(tx.lifecycleFlags())
                    .isEqualTo(expectedLifecycleFlags);
            assertTrue(tx.isActive());
            assertThat(tx.phases())
                    .isEmpty();
        }

        @Test
        @DisplayName("from entity, state, and version")
        void fromEntityStateAndVersion() {
            var entity = createEntity();
            var newState = newState();
            var newVersion = newVersion();

            assertThat(newState)
                    .isNotEqualTo(entity.state());
            assertThat(newVersion)
                    .isNotEqualTo(entity.version());

            var tx = createTx(entity, newState, newVersion);

            assertThat(tx.builder().build())
                    .isEqualTo(newState);
            assertThat(tx.version())
                    .isEqualTo(newVersion);
            assertThat(newState)
                    .isNotEqualTo(entity.state());
            assertThat(newVersion)
                    .isNotEqualTo(entity.version());

            tx.commit();

            // Now test that the state and the version of the entity changed to that from the tx.
            assertThat(entity.state())
                    .isEqualTo(newState);
            assertThat(entity.version())
                    .isEqualTo(newVersion);
        }
    }

    @Test
    @DisplayName("deliver events to handler methods")
    void propagateChangesToAppliers() {
        var entity = createEntity();
        var tx = createTx(entity);

        var event = withMessage(createEventMessage());
        applyEvent(tx, event);

        checkEventReceived(entity, event);
    }

    @Test
    @DisplayName("create phase for each dispatched message")
    void createPhaseForAppliedEvent() {
        var entity = createEntity();
        var tx = createTx(entity);

        var event = withMessage(createEventMessage());
        var outcome = applyEvent(tx, event);
        assertTrue(outcome.hasSuccess());
        assertThat(tx.phases())
                .hasSize(1);

        var phase = tx.phases().get(0);

        assertThat(phase.messageId()).isEqualTo(event.id());
    }

    @Test
    @DisplayName("propagate changes to entity when phase is propagated")
    void propagateChangesToEntityOnCommit() {
        var entity = createEntity();

        var stateBeforePhase = entity.state();
        var versionBeforePhase = entity.version();

        var tx = createTx(entity);
        var event = withMessage(createEventMessage());

        applyEvent(tx, event);

        var modifiedState = entity.state();
        var modifiedVersion = entity.version();

        assertThat(modifiedState)
                .isNotEqualTo(stateBeforePhase);
        assertThat(modifiedVersion)
                .isNotEqualTo(versionBeforePhase);
    }

    @Test
    @DisplayName("not propagate changes to entity on rollback")
    void notPropagateChangesOnRollback() {
        var entity = createEntity();
        var stateBeforeRollback = entity.state();
        var versionBeforeRollback = entity.version();

        var tx = createTx(entity);

        var event = withMessage(createEventMessage());
        var outcome = applyEvent(tx, event);
        assertTrue(outcome.hasSuccess());
        var exception = new RuntimeException("that triggers rollback");
        tx.rollback(fromThrowable(exception));

        var stateAfterRollback = entity.state();
        var versionAfterRollback = entity.version();

        assertThat(stateAfterRollback)
                .isEqualTo(stateBeforeRollback);
        assertThat(versionAfterRollback)
                .isEqualTo(versionBeforeRollback);
    }

    @Test
    @DisplayName("set transaction entity version from event context")
    void setVersionFromEventContext() {
        var entity = createEntity();

        var tx = createTx(entity);
        var event = withMessage(createEventMessage());

        var ctxVersion = event.context()
                              .getVersion();
        assertThat(ctxVersion)
                .isNotEqualTo(tx.version());

        applyEvent(tx, event);
        var modifiedVersion = tx.version();
        assertThat(tx.version())
                .isEqualTo(modifiedVersion);
    }

    @Test
    @DisplayName("notify listener during transaction execution")
    void notifyListenerDuringExecution() {
        var listener = new MemoizingTransactionListener<I>();
        var entity = createEntity();
        var tx = createTx(entity, listener);
        var event = withMessage(createEventMessage());

        var outcome = applyEvent(tx, event);
        assertTrue(outcome.hasSuccess());

        var phases = listener.phasesOnAfter();
        assertThat(phases)
                .hasSize(1);
        var phase = phases.get(0);
        assertThat(phase.messageId())
                .isEqualTo(event.getId());
    }

    @SuppressWarnings("CheckReturnValue") // can ignore new entity version in this test.
    @Test
    @DisplayName("not allow injecting state if entity has non-zero version already")
    void notInjectToEntityWithVersion() {
        var entity = createEntity();
        entity.incrementVersion();
        var newState = newState();
        var newVersion = newVersion();

        assertThrows(IllegalStateException.class,
                     () -> createTx(entity, newState, newVersion));
    }

    @Nested
    @DisplayName("throw")
    class ThrowExceptions {

        @Test
        @DisplayName("`IllegalStateException` on phase failure")
        void onPhaseFailure() {
            var entity = createEntity();

            var tx = createTx(entity);

            var event = withMessage(failingInHandler());

            var outcome = applyEvent(tx, event);
            assertTrue(outcome.hasError());
        }

        @Test
        @DisplayName("`InvalidEntityStateException` on state transition failure")
        void onCommitFailure() {
            var entity = createEntity();
            var tx = createTx(entity);
            var event = withMessage(failingStateTransition());

            var outcome = applyEvent(tx, event);
            assertTrue(outcome.hasError());
        }
    }

    @Nested
    @DisplayName("rollback automatically")
    class RollbackAutomatically {

        @Test
        @DisplayName("on violation in message handler")
        void ifPhaseFailed() {
            var entity = createEntity();
            var originalState = entity.state();
            var originalVersion = entity.version();

            var tx = createTx(entity);

            var event = withMessage(failingInHandler());
            var outcome = applyEvent(tx, event);
            assertTrue(outcome.hasError());
            checkRollback(entity, originalState, originalVersion);
        }

        @Test
        @DisplayName("on violation at state transition")
        void ifCommitFailed() {
            var entity = createAndModify();
            var originalState = entity.state();
            var originalVersion = entity.version();

            var tx = createTx(entity);
            var nextVersion = Versions.increment(entity.version());
            var event = withMessageAndVersion(failingStateTransition(), nextVersion);

            var outcome = applyEvent(tx, event);
            assertTrue(outcome.hasError());
            checkRollback(entity, originalState, originalVersion);
        }

        private E createAndModify() {
            var entity = createEntity();
            var tx = createTx(entity);
            var event = withMessage(createEventMessage());
            applyEvent(tx, event);
            tx.commit();
            return entity;
        }

        private void checkRollback(E entity, S originalState, Version originalVersion) {
            assertNull(entity.transaction());
            assertThat(entity.state())
                    .isEqualTo(originalState);
            assertThat(entity.version())
                    .isEqualTo(originalVersion);
        }
    }

    @Test
    @DisplayName("init builder with the entity ID, if the field is required or assumed required")
    void initBuilderWithId() {
        var entity = createEntity();
        var tx = createTx(entity);
        var firstField =
                entity.state()
                      .getDescriptorForType()
                      .getFields()
                      .get(0);
        assertThat(tx.builder()
                     .getField(firstField))
                .isEqualTo(entity.id());
    }

    /**
     * Call this method in derived transaction tests if corresponding transaction
     * carries version number into an entity.
     *
     * @implNote This method uses package-private API of the {@link Transaction} class.
     */
    protected final void advanceVersionFromEvent() {
        var entity = createEntity();
        var tx = createTx(entity);
        assertThat(tx.version())
                .isEqualTo(entity.version());

        var event = withMessage(createEventMessage());
        applyEvent(tx, event);
        var versionFromEvent = event.context().getVersion();
        assertThat(tx.version())
                .isEqualTo(versionFromEvent);
        tx.commit();
        assertThat(entity.version())
                .isEqualTo(versionFromEvent);
    }
}
