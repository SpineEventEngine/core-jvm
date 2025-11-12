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

import io.spine.core.Event;
import io.spine.server.dispatch.BatchDispatchOutcome;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.Success;
import io.spine.server.test.shared.StringEntity;
import io.spine.server.type.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.spine.core.Versions.increment;
import static io.spine.core.Versions.zero;
import static io.spine.server.type.given.GivenEvent.arbitrary;
import static io.spine.server.type.given.GivenEvent.withVersion;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("rawtypes")   // for simplicity.
@DisplayName("`TransactionalEventPlayer` should")
class TransactionalEventPlayerTest {

    @Test
    @DisplayName("require active transaction to play events")
    void requireActiveTx() {
        assertThrows(IllegalStateException.class,
                     () -> new TxPlayingEntity().play(arbitrary()));
    }

    @Test
    @DisplayName("delegate applying events to transaction when playing")
    void delegateEventsToTx() {
        var entity = entityWithActiveTx(false);
        var txMock = (EventPlayingTransaction) entity.transaction();
        assertNotNull(txMock);
        var v1 = increment(zero());
        var firstEvent = withVersion(v1);
        var secondEvent = withVersion(increment(v1));

        entity.play(newArrayList(firstEvent, secondEvent));

        verifyEventApplied(txMock, firstEvent);
        verifyEventApplied(txMock, secondEvent);
    }

    @SuppressWarnings("unchecked")  // OK for the test.
    private static TxPlayingEntity entityWithActiveTx(boolean txChanged) {
        var entity = new TxPlayingEntity();
        var tx = new StubTransaction(entity, true, txChanged);
        entity.injectTransaction(tx);
        return entity;
    }

    private static void verifyEventApplied(EventPlayingTransaction txMock, Event event) {
        var tx = (StubTransaction) txMock;
        assertTrue(tx.dispatched(event));
    }

    /**
     * Stub implementation of {@code TransactionalEntity}.
     */
    private static class TxPlayingEntity
            extends TransactionalEntity<String, StringEntity, StringEntity.Builder>
            implements EventPlayer {

        private TxPlayingEntity() {
            super("`TxPlayingEntity` ID");
        }

        @Override
        public BatchDispatchOutcome play(Iterable<Event> events) {
            return EventPlayer.forTransactionOf(this)
                              .play(events);
        }
    }

    /**
     * Stub implementation of {@code Transaction} which behaves as told in the passed parameters.
     */
    private static class StubTransaction extends EventPlayingTransaction {

        private final List<Event> dispatchedEvents = new ArrayList<>();

        @SuppressWarnings("unchecked") // Generic parameters are not used by this stub impl.
        private StubTransaction(TransactionalEntity entity, boolean active, boolean stateChanged) {
            super(entity);
            if (!active) {
                deactivate();
            }
            if (stateChanged) {
                markStateChanged();
            }
        }

        @Override
        protected DispatchOutcome dispatch(TransactionalEntity entity, EventEnvelope envelope) {
            var event = envelope.outerObject();
            dispatchedEvents.add(event);
            return DispatchOutcome.newBuilder()
                    .setPropagatedSignal(event.messageId())
                    .setSuccess(Success.getDefaultInstance())
                    .build();
        }

        @Override
        protected VersionIncrement createVersionIncrement(EventEnvelope event) {
            return VersionIncrement.fromEvent(event);
        }

        private boolean dispatched(Event event) {
            return dispatchedEvents.contains(event);
        }
    }
}
