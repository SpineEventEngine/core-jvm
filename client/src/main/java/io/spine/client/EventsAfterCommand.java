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
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.UserId;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    static ImmutableSet<Subscription>
    subscribe(Client client,
              Command command,
              MultiEventConsumers consumers,
              @Nullable ErrorHandler errorHandler) {
        var commandOutcome = new EventsAfterCommand(client, command, consumers);
        var result = commandOutcome.subscribeWith(errorHandler);
        return result;
    }

    private EventsAfterCommand(Client client, Command cmd, MultiEventConsumers consumers) {
        this.client = checkNotNull(client);
        this.command = checkNotDefaultArg(cmd);
        this.user = cmd.actor();
        this.consumers = checkNotNull(consumers);
    }

    /**
     * Subscribes the consumers to the events that originate from the command.
     *
     * <p>If subscribing to a topic fails, cancels the subscriptions made for the topics before
     * it, and rethrows the failure.
     */
    private ImmutableSet<Subscription> subscribeWith(@Nullable ErrorHandler errorHandler) {
        var topics = eventsOf(command);
        var observer = consumers.toObserver(errorHandler);
        var subscriptions = client.subscriptions();
        List<Subscription> made = new ArrayList<>();
        try {
            for (var topic : topics) {
                made.add(subscriptions.subscribeTo(topic, observer));
            }
        } catch (RuntimeException e) {
            made.forEach(subscriptions::cancel);
            throw e;
        }
        return ImmutableSet.copyOf(made);
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
