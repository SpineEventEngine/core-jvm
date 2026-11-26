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

package io.spine.server.stand;

import com.google.protobuf.Any;
import io.spine.base.Identifier;
import io.spine.client.EventUpdates;
import io.spine.client.Filters;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.core.Event;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;


/**
 * The update handler of {@code Subscription}s for {@code Event}s.
 */
final class EventUpdateHandler extends UpdateHandler {

    EventUpdateHandler(Subscription subscription, int targetIndex) {
        super(subscription, targetIndex, targetOf(subscription, targetIndex).type());
    }

    @Override
    Optional<SubscriptionUpdate> detectUpdate(EventEnvelope event) {
        var matches = typeMatches(event) && (includeAll() || matchByFilters(event));
        if (!matches) {
            return Optional.empty();
        }
        var update = createSubscriptionUpdate(event);
        return Optional.of(update);
    }

    @Override
    protected Any extractId(EventEnvelope event) {
        var eventId = event.id();
        var result = Identifier.pack(eventId);
        return result;
    }

    @Override
    boolean typeMatches(EventEnvelope event) {
        var expectedTypeUrl = target().getType();
        var actualTypeUrl = event.typeUrl()
                                 .value();
        return expectedTypeUrl.equals(actualTypeUrl);
    }

    /**
     * Matches an event to the subscription filters.
     */
    private boolean matchByFilters(EventEnvelope event) {
        var idMatches = idMatches(event);
        var eventMatches = eventMatches(event);
        return idMatches && eventMatches;
    }

    /**
     * Creates a subscription update with a single {@link Event} obtained from the envelope.
     */
    private SubscriptionUpdate createSubscriptionUpdate(EventEnvelope event) {
        var updates = extractEventUpdates(event);
        var result = newUpdate()
                .setEventUpdates(updates)
                .build();
        return result;
    }

    private static EventUpdates extractEventUpdates(EventEnvelope event) {
        var eventObject = event.outerObject();
        var result = EventUpdates.newBuilder()
                .addEvent(eventObject)
                .build();
        return result;
    }

    /**
     * Checks if the event message matches the subscription filters.
     */
    private boolean eventMatches(EventEnvelope event) {
        var filters = target().getFilters();
        var evt = event.outerObject();
        var result = filters.getFilterList()
                .stream()
                .map(Filters::toEventFilter)
                .allMatch(f -> f.test(evt));
        return result;
    }
}
