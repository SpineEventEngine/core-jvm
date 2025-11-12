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

package io.spine.server.delivery;

import io.spine.annotation.Internal;
import io.spine.base.Time;
import io.spine.server.delivery.event.ShardProcessed;
import io.spine.server.delivery.event.ShardProcessingRequested;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.Repository;
import io.spine.server.event.AbstractEventReactor;
import io.spine.server.event.React;
import io.spine.server.stand.Stand;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;

import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * The framework-internal process performing the maintenance of delivery shards.
 *
 * <p>Has its own {@link Inbox}, so the messages arriving to it are dispatched
 * by the {@link Delivery}.
 */
final class ShardMaintenanceProcess extends AbstractEventReactor {

    static final TypeUrl TYPE = TypeUrl.of(ShardMaintenance.getDefaultInstance());

    private final Inbox<ShardIndex> inbox;

    /**
     * Creates a new instance of the process, creating the own {@code Inbox} using the passed
     * {@code Delivery}.
     */
    ShardMaintenanceProcess(Delivery delivery) {
        super();
        Inbox.Builder<ShardIndex> builder = delivery.newInbox(TYPE);
        builder.addEventEndpoint(InboxLabel.REACT_UPON_EVENT, EventEndpoint::new);
        this.inbox = builder.build();
    }

    /**
     * By handling this event, guarantees that the messages in the
     * {@linkplain ShardProcessingRequested#getIndex() specified shard} have been processed
     * by the {@link Delivery}.
     */
    @SuppressWarnings("unused")     // see the Javadoc.
    @React
    ShardProcessed on(ShardProcessingRequested event) {
        var now = Time.currentTime();
        var processed = ShardProcessed.newBuilder()
                .setIndex(event.getIndex())
                .setProcess(event.getProcess())
                .setRunInfo(event.getRunInfo())
                .build();
        return processed;
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        var message = (ShardEvent) event.message();
        inbox.send(event)
             .toReactor(message.getIndex());
        return sentToInbox(event, message.getIndex());
    }

    /**
     * Does NOT register this process in {@code Stand}, as the emitted events should
     * not be available for subscribing.
     */
    @Internal
    @Override
    protected void registerIn(Stand stand) {
        // Do nothing.
    }

    /**
     * An endpoint dispatching the events to the parent instance of {@code ShardMaintenanceProcess}.
     */
    private final class EventEndpoint implements MessageEndpoint<ShardIndex, EventEnvelope> {

        private final EventEnvelope envelope;

        private EventEndpoint(EventEnvelope envelope) {
            this.envelope = envelope;
        }

        @Override
        public DispatchOutcome dispatchTo(ShardIndex targetId) {
            return ShardMaintenanceProcess.super.dispatch(envelope);
        }

        @Override
        public void onDuplicate(ShardIndex target, EventEnvelope envelope) {
            // do nothing.
        }

        @Override
        public Repository<ShardIndex, ?> repository() {
            throw newIllegalStateException("`ShardMaintenanceProcess` has no repository.");
        }
    }
}
