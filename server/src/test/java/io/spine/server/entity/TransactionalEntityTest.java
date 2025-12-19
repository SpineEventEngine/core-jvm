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
import io.spine.core.Versions;
import io.spine.server.entity.given.TeEntity;
import io.spine.server.test.shared.EmptyEntity;
import io.spine.validation.ValidatingBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.entity.Transaction.toBuilder;
import static io.spine.testing.TestValues.newUuidValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`TransactionalEntity` should")
@SuppressWarnings("rawtypes")   // for simplicity
class TransactionalEntityTest {

    @Nested
    @DisplayName("be non-changed")
    class BeNonChanged {

        @Test
        @DisplayName("once created")
        void onCreation() {
            assertFalse(newEntity().changed());
        }

        @Test
        @DisplayName("if transaction isn't changed")
        void withUnchangedTx() {
            var entity = entityWithActiveTx(false);

            assertFalse(entity.changed());
        }
    }

    @Nested
    @DisplayName("become changed")
    class BecomeChanged {

        @Test
        @DisplayName("if transaction state changed")
        void ifTxStateChanged() {
            var entity = entityWithActiveTx(true);

            assertTrue(entity.changed());
        }

        @Test
        @DisplayName("once `lifecycleFlags` are updated")
        void onLifecycleFlagsUpdated() {
            TransactionalEntity entity = newEntity();
            entity.setLifecycleFlags(LifecycleFlags.newBuilder()
                                                   .setDeleted(true)
                                                   .build());
            assertTrue(entity.changed());
        }
    }

    @Test
    @DisplayName("have null transaction by default")
    void haveNullTxByDefault() {
        assertNull(newEntity().transaction());
    }

    @Nested
    @DisplayName("have no transaction in progress")
    class HaveNoTxInProgress {

        @Test
        @DisplayName("by default")
        void byDefault() {
            assertFalse(newEntity().isTransactionInProgress());
        }

        @Test
        @DisplayName("until transaction started")
        void untilTxStarted() {
            var entity = newEntity(false, false);

            assertFalse(entity.isTransactionInProgress());
        }
    }

    @Test
    @DisplayName("have transaction in progress when transaction is active")
    void haveTxInProgress() {
        var entity = entityWithActiveTx(false);

        assertTrue(entity.isTransactionInProgress());
    }

    @SuppressWarnings("unchecked")  // OK for the test.
    @Test
    @DisplayName("allow injecting transaction")
    void allowInjectingTx() {
        TransactionalEntity entity = newEntity();
        Transaction tx = new StubTransaction(entity, true, true);
        entity.injectTransaction(tx);

        assertThat(entity.transaction())
                .isEqualTo(tx);
    }

    @SuppressWarnings("unchecked")  // OK for the test.
    @Test
    @DisplayName("disallow injecting transaction wrapped around another entity instance")
    void disallowOtherInstanceTx() {
        var entity = newEntity(true, false);
        var anotherEntity = newEntity(true, false);
        assertThrows(IllegalStateException.class, () ->
                entity.injectTransaction(anotherEntity.tx()));
    }

    @Nested
    @DisplayName("fail to archive")
    class FailToArchive {

        @Test
        @DisplayName("with no transaction")
        void withNoTx() {
            assertThrows(IllegalStateException.class, () -> newEntity().setArchived(true));
        }

        @Test
        @DisplayName("with inactive transaction")
        void withInactiveTx() {
            var entity = newEntity(false, false);
            assertThrows(IllegalStateException.class, () -> entity.setArchived(true));
        }
    }

    @Nested
    @DisplayName("fail to delete")
    class FailToDelete {

        @Test
        @DisplayName("with no transaction")
        void withNoTx() {
            assertThrows(IllegalStateException.class, () -> newEntity().setDeleted(true));
        }

        @Test
        @DisplayName("with inactive transaction")
        void withInactiveTx() {
            var entity = newEntity(false, false);

            assertThrows(IllegalStateException.class, () -> entity.setDeleted(true));
        }
    }

    @Test
    @DisplayName("return transaction `lifecycleFlags` if transaction is active")
    void returnActiveTxFlags() {
        var entity = newEntity(true, true);
        var originalFlags = entity.lifecycleFlags();

        var tx = entity.transaction();

        assertThat(originalFlags)
                .isEqualTo(tx.lifecycleFlags());

        ((TeEntity) entity).turnToDeleted();

        assertThat(originalFlags)
                .isNotEqualTo(tx.lifecycleFlags());
    }

    @Nested
    @DisplayName("return builder from state")
    class ReturnBuilderFromState {

        @Test
        @DisplayName("which is non-null")
        void nonNull() {
            ValidatingBuilder builder = toBuilder(newEntity());
            assertNotNull(builder);
        }

        @Test
        @DisplayName("which reflects current state")
        void reflectingCurrentState() {
            TransactionalEntity<?, ?, ?> entity = newEntity();
            EntityState originalState = toBuilder(entity)
                    .build();

            var newState = EmptyEntity.newBuilder()
                    .setId(newUuidValue().getValue())
                    .build();
            assertThat(newState)
                    .isNotEqualTo(originalState);

            TestTransaction.injectState(entity, newState, Versions.zero());
            EntityState modifiedState = toBuilder(entity)
                    .build();

            assertThat(newState)
                    .isEqualTo(modifiedState);
        }
    }

    private static TransactionalEntity<?, ?, ?> newEntity() {
        return new TeEntity("TransactionalId");
    }

    private static TransactionalEntity entityWithActiveTx(boolean txChanged) {
        return newEntity(true, txChanged);
    }

    @SuppressWarnings("unchecked")
    static  // OK for the test.
    TransactionalEntity newEntity(boolean activeTx, boolean stateChanged) {
        TransactionalEntity entity = newEntity();
        Transaction tx = new StubTransaction(entity, activeTx, stateChanged);
        entity.injectTransaction(tx);
        return entity;
    }
}
