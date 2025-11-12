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

import static io.spine.core.Responses.ok;

/**
 * The update handler of {@code Subscription}s for {@code Event}s.
 */
final class EventUpdateHandler extends UpdateHandler {

    EventUpdateHandler(Subscription subscription) {
        super(subscription, subscription.targetType());
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
        var result = SubscriptionUpdate.newBuilder()
                .setSubscription(subscription())
                .setResponse(ok())
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
