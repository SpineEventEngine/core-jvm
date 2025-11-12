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
import io.spine.annotation.Internal;
import io.spine.server.ServerEnvironment;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.delivery.InboxLabel.CATCH_UP;
import static io.spine.server.delivery.InboxLabel.HANDLE_COMMAND;
import static io.spine.server.delivery.InboxLabel.IMPORT_EVENT;
import static io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT;
import static io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER;

/**
 * A container for the messages dispatched to a certain consumer, such as an event subscriber
 * or a command assignee.
 *
 * <p>Serves as a pre-stage allowing to filter, deduplicate and reorder messages before they
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

    private Inbox(Builder<I> builder, Delivery delivery) {
        this.entityStateType = builder.entityStateType;
        this.commandPart = new InboxOfCommands<>(builder);
        this.eventPart = new InboxOfEvents<>(builder);
        this.delivery = delivery;
        this.batchDispatcher = builder.batchDispatcher;
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
     * Sends an event envelope to the {@code Inbox} and allows to set a destination for this
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
     * Sends a command envelope to the {@code Inbox} and allows to set a destination for this
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
     * Returns an {@code Inbox}-specific mechanism of delivery the previously sharded messages
     * to the endpoints configured for this {@code Inbox} instance.
     */
    public ShardedMessageDelivery<InboxMessage> delivery() {
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
         * Adds an endpoint for events which will be delivered through the {@code Inbox} and
         * marks it with the certain label.
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
         * Adds an endpoint for commands which will be delivered through the {@code Inbox} and
         * marks it with the certain label.
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
         * Allows to specify the listener of the starting and ending batch dispatching operations.
         */
        @CanIgnoreReturnValue
        public Builder<I> withBatchListener(BatchDeliveryListener<I> dispatcher) {
            this.batchDispatcher = checkNotNull(dispatcher);
            return this;
        }

        /**
         * Creates an instance of {@code Inbox} and registers it in the
         * server-wide {@code Delivery}.
         */
        public Inbox<I> build() {
            var delivery = ServerEnvironment.instance()
                                            .delivery();
            checkNotNull(entityStateType, "Entity state type must be set.");
            checkArgument(!eventEndpoints.isEmpty() || !commandEndpoints.isEmpty(),
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
         * Sends to the event-importing receptor of the entity with the specified ID.
         */
        public void toImporter(I entityId) {
            eventPart.store(event, entityId, IMPORT_EVENT);
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
