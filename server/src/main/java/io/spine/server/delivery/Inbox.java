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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.core.BoundedContextName;
import io.spine.server.ServerEnvironment;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.delivery.InboxLabel.CATCH_UP;
import static io.spine.server.delivery.InboxLabel.HANDLE_COMMAND;
import static io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT;
import static io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER;

/**
 * A container for the messages dispatched to a certain consumer, such as an event subscriber
 * or a command assignee.
 *
 * <p>Serves as a pre-stage allowing one to filter, deduplicate and reorder messages before they
 * are dispatched to their destination.
 *
 * @param <I>
 *         the type of consumer identifiers.
 */
@Internal
public final class Inbox<I> {

    private final TypeUrl entityStateType;
    private final InboxOfCommands<I> commandPart;
    private final InboxOfEvents<I> eventPart;
    private final Delivery delivery;
    private final @Nullable BatchDeliveryListener<I> batchDispatcher;
    private final @Nullable BoundedContextName contextName;

    private Inbox(Builder<I> builder, Delivery delivery) {
        this.entityStateType = builder.entityStateType;
        this.commandPart = new InboxOfCommands<>(builder);
        this.eventPart = new InboxOfEvents<>(builder);
        this.delivery = delivery;
        this.batchDispatcher = builder.batchDispatcher;
        this.contextName = builder.contextName;
    }

    /**
     * Creates an instance of {@code Builder} with the given consumer identifier.
     *
     * @param typeUrl
     *         the type URL of a consumer
     */
    static <I> Builder<I> newBuilder(TypeUrl typeUrl, InboxWriter writer) {
        return new Builder<>(typeUrl, writer);
    }

    /**
     * Sends an event envelope to the {@code Inbox} and allows one to set a destination for this
     * message, determining its processing destiny.
     *
     * @param event
     *         the event to put to {@code Inbox}
     * @return the choice of destination for an event message available in this {@code Inbox}
     */
    public EventDestinations send(EventEnvelope event) {
        checkNotNull(event);
        return new EventDestinations(event);
    }

    /**
     * Sends a command envelope to the {@code Inbox} and allows one to set a destination for this
     * message, determining its processing destiny.
     *
     * @param command
     *         the command to put to {@code Inbox}
     * @return the choice of destination for a command message available in this {@code Inbox}
     */
    public CommandDestinations send(CommandEnvelope command) {
        checkNotNull(command);
        return new CommandDestinations(command);
    }

    /**
     * Returns the state type of entities served by this {@code Inbox}.
     */
    public TypeUrl entityStateType() {
        return entityStateType;
    }

    /**
     * Returns the name of the Bounded Context, to which this {@code Inbox} belongs,
     * or {@code null} if it was not {@linkplain Builder#withContext(BoundedContextName) set}.
     */
    @Nullable BoundedContextName contextName() {
        return contextName;
    }

    /**
     * Returns an {@code Inbox}-specific mechanism of delivery the previously sharded messages
     * to the endpoints configured for this {@code Inbox} instance.
     */
    ShardedMessageDelivery<InboxMessage> delivery() {
        return new TargetDelivery<>(commandPart, eventPart, batchDispatcher);
    }

    /**
     * Unregisters this {@code Inbox} instance in the JVM-wide {@code Delivery}.
     *
     * After this call the messages residing in the sharded storage will not be delivered to the
     * entities served by this {@code Inbox}.
     */
    public void unregister() {
        delivery.unregister(this);
    }

    /**
     * A builder of {@link Inbox} instances.
     *
     * @param <I>
     *         the type of identifier of the objects, for which the {@code Inbox} is built
     */
    public static final class Builder<I> {

        private final TypeUrl entityStateType;
        private final InboxWriter writer;
        private final Endpoints<I, EventEnvelope> eventEndpoints = new Endpoints<>();
        private final Endpoints<I, CommandEnvelope> commandEndpoints = new Endpoints<>();
        private @Nullable BatchDeliveryListener<I> batchDispatcher;
        private @Nullable BoundedContextName contextName;

        /**
         * Creates an instance of {@code Builder} for the given {@code Inbox} consumer entity type.
         *
         * @param type
         *         the type URL of the entity, to which belongs the {@code Inbox} being built
         * @param writer
         *         the writer to use when messages are sent via the inbox being built
         */
        private Builder(TypeUrl type, InboxWriter writer) {
            this.entityStateType = type;
            this.writer = writer;
        }

        /**
         * Adds an endpoint for events that will be delivered through the {@code Inbox} and
         * marks it with a certain label.
         */
        @CanIgnoreReturnValue
        public Builder<I> addEventEndpoint(InboxLabel label,
                                           LazyEndpoint<I, EventEnvelope> lazyEndpoint) {
            checkNotNull(label);
            checkNotNull(lazyEndpoint);
            eventEndpoints.add(label, lazyEndpoint);
            return this;
        }

        /**
         * Adds an endpoint for commands that will be delivered through the {@code Inbox} and
         * marks it with a certain label.
         */
        @CanIgnoreReturnValue
        public Builder<I> addCommandEndpoint(InboxLabel label,
                                             LazyEndpoint<I, CommandEnvelope> lazyEndpoint) {
            checkNotNull(label);
            checkNotNull(lazyEndpoint);
            commandEndpoints.add(label, lazyEndpoint);
            return this;
        }

        /**
         * Allows one to specify the listener of the starting and ending batch
         * dispatching operations.
         */
        @CanIgnoreReturnValue
        public Builder<I> withBatchListener(BatchDeliveryListener<I> dispatcher) {
            this.batchDispatcher = checkNotNull(dispatcher);
            return this;
        }

        /**
         * Sets the name of the Bounded Context, to which the {@code Inbox} belongs.
         *
         * <p>The {@code Delivery} needs it for the inboxes of the catch-up machinery, because
         * every System context has inboxes of these types.
         */
        @CanIgnoreReturnValue
        public Builder<I> withContext(BoundedContextName name) {
            this.contextName = checkNotNull(name);
            return this;
        }

        /**
         * Tells if at least one event or command endpoint was added to this builder.
         *
         * <p>Since {@link #build()} requires an endpoint, this method tells if the builder
         * is ready to be built.
         */
        public boolean hasEndpoints() {
            return !eventEndpoints.isEmpty() || !commandEndpoints.isEmpty();
        }

        /**
         * Creates an instance of {@code Inbox} and registers it in the
         * server-wide {@code Delivery}.
         *
         * @throws IllegalArgumentException
         *         if neither an event nor a command endpoint was added; use
         *         {@link #hasEndpoints()} to tell in advance
         */
        public Inbox<I> build() {
            var delivery = ServerEnvironment.instance()
                                            .delivery();
            checkNotNull(entityStateType, "Entity state type must be set.");
            checkArgument(hasEndpoints(),
                          "There must be at least one event or command endpoint.");
            var inbox = new Inbox<>(this, delivery);
            delivery.register(inbox);
            return inbox;
        }

        Endpoints<I, EventEnvelope> eventEndpoints() {
            return eventEndpoints;
        }

        Endpoints<I, CommandEnvelope> commandEndpoints() {
            return commandEndpoints;
        }

        InboxWriter writer() {
            return writer;
        }

        TypeUrl entityStateType() {
            return entityStateType;
        }
    }

    /**
     * The available destinations for the {@code Event}s sent via this inbox.
     */
    public final class EventDestinations {

        private final EventEnvelope event;

        private EventDestinations(EventEnvelope event) {
            this.event = event;
        }

        /**
         * Sends to the reacting receptor of the entity with the specified ID.
         */
        public void toReactor(I entityId) {
            eventPart.store(event, entityId, REACT_UPON_EVENT);
        }

        /**
         * Sends to the event-subscribing receptor of the entity with the specified ID.
         */
        public void toSubscriber(I entityId) {
            eventPart.store(event, entityId, UPDATE_SUBSCRIBER);
        }

        /**
         * Sends to the catch-up receptor of the entity with the specified ID.
         */
        public void toCatchUp(I entityId) {
            eventPart.store(event, entityId, CATCH_UP);
        }
    }

    /**
     * The available destinations for the {@code Commands}s sent via this inbox.
     */
    public final class CommandDestinations {

        private final CommandEnvelope command;

        private CommandDestinations(CommandEnvelope command) {
            this.command = command;
        }

        /**
         * Sends to the command handling method of the entity with the specified ID.
         */
        public void toHandler(I entityId) {
            commandPart.store(command, entityId, HANDLE_COMMAND);
        }
    }
}
