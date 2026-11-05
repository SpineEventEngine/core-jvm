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

package io.spine.client;

import com.google.common.collect.ImmutableSet;
import io.grpc.stub.StreamObserver;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.UserId;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.NonNull;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.client.Filters.eq;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * Subscribes to events that originate from the given command and arranges the delivery
  * to the passed event consumers.
 */
final class EventsAfterCommand implements WithLogging {

    private final Client client;
    private final UserId user;
    private final Command command;
    private final MultiEventConsumers consumers;

    /**
     * Subscribes to the events of the types consumed by the given consumers that originate
     * from the given command.
     *
     * @param client
     *         the client to subscribe with
     * @param command
     *         the command whose events to receive
     * @param consumers
     *         the consumers which tell the types of the events
     * @param observer
     *         the observer to deliver the events to
     * @return the created subscriptions
     */
    static @NonNull ImmutableSet<Subscription>
    subscribe(Client client,
              Command command,
              MultiEventConsumers consumers,
              StreamObserver<Event> observer) {
        var commandOutcome = new EventsAfterCommand(client, command, consumers);
        var result = commandOutcome.subscribeWith(checkNotNull(observer));
        return result;
    }

    private EventsAfterCommand(Client client, Command cmd, MultiEventConsumers consumers) {
        this.client = checkNotNull(client);
        this.command = checkNotDefaultArg(cmd);
        this.user = cmd.actor();
        this.consumers = checkNotNull(consumers);
    }

    private ImmutableSet<Subscription> subscribeWith(StreamObserver<Event> observer) {
        var topics = eventsOf(command);
        var subscriptions =
                topics.stream()
                      .map((topic) -> client.subscriptions()
                                            .subscribeTo(topic, observer))
                      .collect(toImmutableSet());
        return subscriptions;
    }

    /**
     * Creates subscription topics for the subscribed events that have the passed command
     * as the origin.
     */
    private ImmutableSet<Topic> eventsOf(Command c) {
        var pastMessage =
                EventContext.Field.pastMessage()
                                  .getField();
        var fieldName =
                Event.Field.context()
                           .getField()
                           .nested(pastMessage)
                           .toString();
        var eventTypes = consumers.eventTypes();
        var topic = client.requestOf(user)
                          .topic();
        var topics =
                eventTypes.stream()
                          .map((eventType) -> topic.select(eventType)
                                                   .where(eq(fieldName, c.asMessageOrigin()))
                                                   .build())
                          .collect(toImmutableSet());
        return topics;
    }
}
