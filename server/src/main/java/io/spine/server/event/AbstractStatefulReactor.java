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

package io.spine.server.event;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.base.Errors;
import io.spine.base.Identifier;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.Inbox;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.delivery.MessageEndpoint;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.Repository;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;
import io.spine.validation.ValidatingBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * An abstract base for {@link AbstractEventReactor}s that have state and require
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

    /**
     * The locks held while dispatching an event to a target, one per state type.
     *
     * <p>All the reactors of a state type share the lock. So, the lock stays the same when
     * a reactor created later takes over the delivery of the events of the type, while
     * an earlier delivery is still in progress.
     */
    private static final Map<TypeUrl, Object> endpointLocks = new ConcurrentHashMap<>();

    private final Inbox<I> inbox;
    private final Object endpointLock;

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
        this.endpointLock = endpointLocks.computeIfAbsent(stateType, type -> new Object());
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

    /**
     * Obtains the reactor that receives the events sent to the target with the given ID.
     *
     * <p>If several reactors of the same state type exist, the {@code Delivery} delivers
     * the events of all of them through the reactor created last. A descendant that has
     * several instances overrides this method to return the instance serving the target.
     *
     * <p>Returns this reactor by default.
     *
     * @param targetId
     *         the ID of the target
     * @return the reactor serving the target
     * @throws IllegalStateException
     *         if no reactor serves the target; the event is not dispatched in that case,
     *         and the {@code DeliveryMonitor} is notified of a failed reception
     */
    @Internal
    protected AbstractStatefulReactor<I, S, B> reactorFor(I targetId) {
        return this;
    }

    /**
     * Loads the state of the target into the builder, dispatches the event to this reactor,
     * and stores the changes made to the builder.
     */
    private DispatchOutcome receive(EventEnvelope envelope, I targetId) {
        loadIntoBuilder(targetId);
        var outcome = super.dispatch(envelope);
        flushState();
        return outcome;
    }

    @SuppressWarnings("unchecked") // `B` is the builder of `S`, as this class declares.
    private void loadIntoBuilder(I id) {
        var existingState = load(id);
        builder = existingState.isPresent()
                  ? (B) existingState.get()
                                     .toBuilder()
                  : newStateBuilderWith(id);
    }

    private final class EventEndpoint implements MessageEndpoint<I, EventEnvelope> {

        private final EventEnvelope envelope;

        private EventEndpoint(EventEnvelope envelope) {
            this.envelope = envelope;
        }

        /**
         * Dispatches the wrapped event to the specified target through the reactor
         * {@linkplain #reactorFor(Object) serving} that target.
         *
         * @implNote Holds the lock shared by all the reactors of the state type, so that
         *         a single thread at a time dispatches the events of the type, whichever
         *         reactor delivers them. This prevents concurrent modifications when running
         *         locally in a multi-threading mode, also while a reactor created later
         *         takes over the delivery from an earlier one. A lock per serving reactor
         *         would not do: a reactor may deliver the events of other shards
         *         synchronously while it holds the lock, and the locks of several serving
         *         reactors, taken in such nested deliveries, could deadlock.
         */
        @Override
        public DispatchOutcome dispatchTo(I targetId) {
            synchronized (endpointLock) {
                AbstractStatefulReactor<I, S, B> reactor;
                try {
                    reactor = reactorFor(targetId);
                } catch (IllegalStateException e) {
                    return noReactorFor(targetId, e);
                }
                return reactor.receive(envelope, targetId);
            }
        }

        private DispatchOutcome noReactorFor(I targetId, IllegalStateException cause) {
            logger().atWarning().withCause(cause).log(() -> format(
                    "Cannot dispatch the event `%s` to the target `%s`.",
                    envelope.messageClass(), Identifier.toString(targetId)));
            var outcome = DispatchOutcome
                    .newBuilder()
                    .setPropagatedSignal(envelope.messageId())
                    .setError(Errors.fromThrowable(cause))
                    .build();
            return outcome;
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
    }
}
