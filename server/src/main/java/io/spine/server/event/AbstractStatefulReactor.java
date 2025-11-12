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

package io.spine.server.event;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.Inbox;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.delivery.MessageEndpoint;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.Repository;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;
import io.spine.validate.ValidatingBuilder;

import java.util.Optional;
import java.util.Set;

import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * An abstract base for {@link AbstractEventReactor}s which have state and require
 * {@linkplain io.spine.server.delivery.Delivery delivering} the messages through an
 * {@link io.spine.server.delivery.Inbox Inbox}.
 *
 * @param <I>
 *         the type of the reactor identifiers
 * @param <S>
 *         the type of reactor states
 * @param <B>
 *         the type of the builders for the reactor states
 */
public abstract class AbstractStatefulReactor<I, S extends Message, B extends ValidatingBuilder<S>>
        extends AbstractEventReactor {

    private final Inbox<I> inbox;
    private final Object endpointLock = new Object();

    private B builder;

    /**
     * Returns the current state as a {@code ValidatingBuilder} for the respective message.
     */
    protected B builder() {
        return builder;
    }

    /**
     * Creates a new instance of the reactor and initializes the {@link Inbox} for it.
     */
    protected AbstractStatefulReactor(TypeUrl stateType) {
        super();
        var delivery = ServerEnvironment.instance()
                                        .delivery();
        this.inbox = configureInbox(delivery, stateType);
    }

    private Inbox<I> configureInbox(Delivery delivery, TypeUrl stateType) {
        Inbox.Builder<I> builder = delivery.newInbox(stateType);
        return builder.addEventEndpoint(InboxLabel.REACT_UPON_EVENT, EventEndpoint::new)
                      .build();
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        Set<I> targets = route(event);
        for (var target : targets) {
            inbox.send(event)
                 .toReactor(target);
        }
        var outcome = sentToInbox(event, targets);
        return outcome;
    }

    /**
     * Loads the state from the storage by ID.
     *
     * <p>Returns {@code Optional.empty()} if there is no such object found.
     */
    protected abstract Optional<S> load(I id);

    /**
     * Stores the passed state in the storage.
     */
    protected abstract void store(S updatedState);

    /**
     * Creates a new instance of the respective state {@link ValidatingBuilder} and sets the
     * passed identifier to it.
     */
    protected abstract B newStateBuilderWith(I id);

    /**
     * Immediately writes the changes made to the current {@linkplain #builder() builder} to the
     * storage.
     *
     * <p>The builder state is validated prior to storing. Any validation exceptions are propagated
     * as-is.
     */
    protected final void flushState() {
        var newState = builder().build();
        store(newState);
    }

    /**
     * Selects the target to which the event should be dispatched.
     *
     * @param event
     *         the event to dispatch
     * @return the set of the target identifiers
     */
    protected abstract ImmutableSet<I> route(EventEnvelope event);

    private final class EventEndpoint implements MessageEndpoint<I, EventEnvelope> {

        private final EventEnvelope envelope;

        private EventEndpoint(EventEnvelope envelope) {
            this.envelope = envelope;
        }

        /**
         * Loads the state of the builder, dispatches the wrapped event to the specified target
         * and updates the storage with the changes made to the builder.
         *
         * @implNote Can only be executed by a single thread at a time to avoid any
         *         concurrent modifications when running locally in a multi-threading mode
         */
        @Override
        public DispatchOutcome dispatchTo(I targetId) {
            synchronized (endpointLock) {
                loadIntoBuilder(targetId);
                var outcome = AbstractStatefulReactor.super.dispatch(envelope);
                flushState();
                return outcome;
            }
        }

        /**
         * Does nothing, as the event reactors should not care about the duplicates due to their
         * framework-internal essence.
         */
        @Override
        public void onDuplicate(I target, EventEnvelope envelope) {
            // Do nothing.
        }

        /**
         * Always throws the {@link IllegalStateException}, as there can be no repository for the
         * event reactors.
         *
         * @throws IllegalStateException
         *         always
         */
        @Override
        public Repository<I, ?> repository() throws IllegalStateException {
            throw newIllegalStateException("`%s` has no repository.", getClass());
        }

        @SuppressWarnings("unchecked")
        private void loadIntoBuilder(I id) {
            var existingState = load(id);
            AbstractStatefulReactor.this.builder =
                    existingState.isPresent()
                    ? (B) existingState.get()
                                       .toBuilder()
                    : newStateBuilderWith(id);
        }
    }
}
