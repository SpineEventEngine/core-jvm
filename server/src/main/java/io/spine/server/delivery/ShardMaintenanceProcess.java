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

package io.spine.server.delivery;

import io.spine.annotation.Internal;
import io.spine.base.Errors;
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

import static io.spine.server.delivery.CatchUpProcess.processOf;
import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * The framework-internal process performing the maintenance of delivery shards.
 *
 * <p>Each {@link CatchUpProcess} has a shard maintenance process of its own, registered in
 * the same Bounded Context. The shard maintenance process handles the requests of that
 * catch-up process only.
 *
 * <p>Has its own {@link Inbox}, so the messages arriving to it are dispatched
 * by the {@link Delivery}. As the {@code Delivery} keeps one {@code Inbox} per state type,
 * it delivers the requests of all catch-up processes through the {@code Inbox} of
 * the shard maintenance process created last. That process passes each request to the shard
 * maintenance process of the catch-up process that made the request. Thus, the resulting
 * {@code ShardProcessed} event is posted in the Bounded Context of that catch-up process.
 */
final class ShardMaintenanceProcess extends AbstractEventReactor {

    static final TypeUrl TYPE = TypeUrl.of(ShardMaintenance.getDefaultInstance());

    private final Inbox<ShardIndex> inbox;

    /**
     * The type URL of the projections caught up by the catch-up process of this
     * shard maintenance process.
     */
    private final String projectionType;

    /**
     * Creates a new instance of the process, creating its own {@code Inbox} using the passed
     * {@code Delivery}.
     *
     * @param delivery
     *         the {@code Delivery} to create the {@code Inbox} with
     * @param projectionType
     *         the type of the projections caught up by the catch-up process
     *         for which the new process maintains the shards
     */
    ShardMaintenanceProcess(Delivery delivery, TypeUrl projectionType) {
        super();
        this.projectionType = projectionType.value();
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

    /**
     * {@inheritDoc}
     *
     * <p>Accepts only the shard processing requests made by the catch-up process of this
     * shard maintenance process. Thus, a Bounded Context with several catch-up processes
     * stores each request once.
     */
    @Override
    public boolean canDispatch(EventEnvelope event) {
        return event.message() instanceof ShardProcessingRequested request
                && projectionType.equals(request.getProcess()
                                                .getProjectionType());
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        var message = (ShardEvent) event.message();
        inbox.send(event)
             .toReactor(message.getIndex());
        return sentToInbox(event, message.getIndex());
    }

    /**
     * Reacts to the event and posts the reaction to the Bounded Context
     * in which this process is registered.
     */
    private DispatchOutcome react(EventEnvelope event) {
        return super.dispatch(event);
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
     * An endpoint dispatching the events to the shard maintenance process of the catch-up
     * process that requested the processing of a shard.
     */
    private final class EventEndpoint implements MessageEndpoint<ShardIndex, EventEnvelope> {

        private final EventEnvelope envelope;

        private EventEndpoint(EventEnvelope envelope) {
            this.envelope = envelope;
        }

        @Override
        public DispatchOutcome dispatchTo(ShardIndex targetId) {
            var request = (ShardProcessingRequested) envelope.message();
            ShardMaintenanceProcess maintenance;
            try {
                maintenance = processOf(request.getProcess()).maintenance();
            } catch (IllegalStateException e) {
                return noProcessFor(e);
            }
            return maintenance.react(envelope);
        }

        private DispatchOutcome noProcessFor(IllegalStateException cause) {
            logger().atWarning().withCause(cause).log(() -> format(
                    "Cannot dispatch the event `%s` to a shard maintenance process.",
                    envelope.messageClass()));
            var outcome = DispatchOutcome
                    .newBuilder()
                    .setPropagatedSignal(envelope.messageId())
                    .setError(Errors.fromThrowable(cause))
                    .build();
            return outcome;
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
