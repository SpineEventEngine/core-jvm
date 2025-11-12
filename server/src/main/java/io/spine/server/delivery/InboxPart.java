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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.Time;
import io.spine.server.ServerEnvironment;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.NoReaction;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.type.SignalEnvelope;
import io.spine.type.TypeUrl;

/**
 * An abstract base of {@link Inbox inbox} part.
 *
 * <p>Commands and events which are delivered to their targets through inbox are treated
 * in a similar way, but still there is some difference in storage mechanism and measures
 * taken in case of some runtime issues (e.g. duplication). Therefore the inbox is split into
 * parts, specific to each of the message types.
 *
 * <p>Descendants define the behaviour specific to the signal type.
 *
 * @param <I>
 *         the type of identifier or inbox target entities
 * @param <M>
 *         the type of message envelopes, which are served by this inbox part
 */
abstract class InboxPart<I, M extends SignalEnvelope<?, ?, ?>> {

    private final Endpoints<I, M> endpoints;
    private final InboxWriter writer;
    private final TypeUrl entityStateType;

    InboxPart(Inbox.Builder<I> builder, Endpoints<I, M> endpoints) {
        this.endpoints = endpoints;
        this.writer = builder.writer();
        this.entityStateType = builder.entityStateType();
    }

    /**
     * Fetches the message object wrapped into the {@code envelope} and sets it as a payload of
     * the record further passed to storage.
     */
    protected abstract void setRecordPayload(M envelope, InboxMessage.Builder builder);

    /**
     * Extracts the UUID identifier value from the envelope wrapping the passed object.
     */
    protected abstract String extractUuidFrom(M envelope);

    /**
     * Extracts the message object passed inside the {@code InboxMessage} as an envelope.
     */
    protected abstract M asEnvelope(InboxMessage message);

    /**
     * Determines the status of the message.
     */
    protected InboxMessageStatus determineStatus(M message,
                                                 InboxLabel label) {
        return InboxMessageStatus.TO_DELIVER;
    }

    void store(M envelope, I entityId, InboxLabel label) {
        var inboxId = InboxIds.wrap(entityId, entityStateType);
        var delivery = ServerEnvironment.instance()
                                        .delivery();
        var shardIndex = delivery.whichShardFor(entityId, entityStateType);
        var id = InboxMessageMixin.generateIdWith(shardIndex);
        var builder = InboxMessage.newBuilder()
                .setId(id)
                .setSignalId(signalIdFrom(envelope, entityId))
                .setInboxId(inboxId)
                .setLabel(label)
                .setWhenReceived(Time.currentTime())
                .setStatus(determineStatus(envelope, label))
                .setVersion(VersionCounter.next());
        setRecordPayload(envelope, builder);
        var message = builder.build();

        TenantAwareRunner
                .with(envelope.tenantId())
                .run(() -> writer.write(message));
    }

    private InboxSignalId signalIdFrom(M envelope, I targetId) {
        var uuid = extractUuidFrom(envelope);
        return InboxIds.newSignalId(targetId, uuid);
    }

    /**
     * Delivers the message to its message endpoint.
     */
    DispatchOutcome deliver(InboxMessage message) {
        var call = new EndpointCall<DispatchOutcome>(message) {
            @Override
            DispatchOutcome doInvoke(MessageEndpoint<I, M> endpoint, I targetId, M envelope) {
                return endpoint.dispatchTo(targetId);
            }
        };
        return call.invoke();
    }

    /**
     * Notifies the message endpoint of the duplicate.
     */
    void notifyOfDuplicated(InboxMessage message) {
        var call = new EndpointCall<NoReaction>(message) {

            @Override
            NoReaction doInvoke(MessageEndpoint<I, M> endpoint, I targetId, M envelope) {
                endpoint.onDuplicate(targetId, envelope);
                return NoReaction.getDefaultInstance();
            }
        };
        call.invoke();
    }

    /**
     * Passes the message to the endpoint and returns the resut of the invocation, if any.
     *
     * @param <R>
     *     the type of the invocation result
     */
    private abstract class EndpointCall<R> {

        private final MessageEndpoint<I, M> endpoint;
        private final I targetId;
        private final M envelope;

        @SuppressWarnings("unchecked")    // Only IDs of type `I` are stored.
        private EndpointCall(InboxMessage message) {
            this.envelope = asEnvelope(message);
            var label = message.getLabel();
            var inboxId = message.getInboxId();
            this.endpoint =
                    endpoints.get(label, envelope)
                             .orElseThrow(() -> new LabelNotFoundException(inboxId, label));
            this.targetId = (I) InboxIds.unwrap(message.getInboxId());
        }

        @CanIgnoreReturnValue
        public R invoke() {
            return TenantAwareRunner
                    .with(envelope.tenantId())
                    .evaluate(() -> doInvoke(endpoint, targetId, envelope));
        }

        /**
         * Invokes the method of the endpoint taking the ID of the target
         * and the envelope as args, if needed.
         */
        abstract R doInvoke(MessageEndpoint<I, M> endpoint, I targetId, M envelope);
    }
}
