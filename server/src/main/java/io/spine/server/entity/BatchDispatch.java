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
import io.spine.core.MessageId;
import io.spine.server.dispatch.BatchDispatchOutcome;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.Interruption;
import io.spine.server.type.EventEnvelope;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A process of propagation of a number of events.
 */
final class BatchDispatch {

    private final EventPlayingTransaction<?, ?, ?, ?> transaction;

    private final BatchDispatchOutcome.Builder propagation = BatchDispatchOutcome.newBuilder();
    private boolean successful = true;
    private MessageId lastMessage = MessageId.getDefaultInstance();

    BatchDispatch(EventPlayingTransaction<?, ?, ?, ?> transaction) {
        this.transaction = checkNotNull(transaction);
        propagation.setTargetEntity(transaction.entityId());
    }

    /**
     * Propagates the given event and memorizes the propagation result.
     */
    void play(Event event) {
        if (successful) {
            var eventEnvelope = EventEnvelope.of(event);
            var outcome = transaction.play(eventEnvelope);
            propagation.addOutcome(outcome);
            successful = !outcome.hasError();
            lastMessage = event.messageId();
        } else {
            var interruption = Interruption.newBuilder()
                    .setStoppedAt(lastMessage)
                    .buildPartial();
            var outcome = DispatchOutcome.newBuilder()
                    .setPropagatedSignal(event.messageId())
                    .setInterrupted(interruption)
                    .build();
            propagation.addOutcome(outcome);
        }
    }

    /**
     * Obtains the summary of all the propagates events.
     */
    BatchDispatchOutcome summary() {
        return propagation
                .setSuccessful(successful)
                .build();
    }
}
