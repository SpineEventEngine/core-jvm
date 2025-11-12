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

package io.spine.server.event.store;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.base.Field;
import io.spine.base.FieldFilter;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.server.event.EventFilter;
import io.spine.type.TypeName;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import static io.spine.protobuf.AnyPacker.unpackFunc;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static java.util.stream.Collectors.toList;

/**
 * The predicate for filtering events by {@link EventFilter}.
 */
final class MatchFilter implements Predicate<Event> {

    /**
     * The type URL of events to accept.
     *
     * <p>If null, all events are accepted.
     */
    private final @Nullable TypeUrl eventTypeUrl;

    /**
     * The list of aggregate IDs of which events to accept.
     *
     * <p>If null, all IDs are accepted.
     */
    private final @Nullable List<Any> aggregateIds;

    private final Collection<FieldFilter> eventFieldFilters;
    private final Collection<FieldFilter> contextFieldFilters;

    MatchFilter(EventFilter filter) {
        this.eventTypeUrl = getEventTypeUrl(filter);
        this.aggregateIds = getAggregateIdentifiers(filter);
        this.eventFieldFilters = filter.getEventFieldFilterList();
        this.contextFieldFilters = filter.getContextFieldFilterList();
    }

    private static @Nullable TypeUrl getEventTypeUrl(EventFilter filter) {
        var eventType = filter.getEventType();
        var result = eventType.isEmpty()
                     ? null
                     : TypeName.of(eventType)
                               .toUrl();
        return result;
    }

    private static @Nullable List<Any> getAggregateIdentifiers(EventFilter filter) {
        var aggregateIdList = filter.getAggregateIdList();
        var result = aggregateIdList.isEmpty()
                     ? null
                     : aggregateIdList;
        return result;
    }

    @Override
    public boolean test(@Nullable Event event) {
        if (event == null) {
            return false;
        }

        var eventMessage = event.enclosedMessage();
        var context = event.context();

        if (!checkEventType(eventMessage)) {
            return false;
        }

        if (!checkAggregateIds(context)) {
            return false;
        }

        if (!check(eventMessage, eventFieldFilters)) {
            return false;
        }

        var result = check(context, contextFieldFilters);
        return result;
    }

    private boolean checkAggregateIds(EventContext context) {
        if (aggregateIds == null) {
            return true;
        }
        var aggregateId = context.getProducerId();
        var result = aggregateIds.contains(aggregateId);
        return result;
    }

    private boolean checkEventType(EventMessage event) {
        var result = (eventTypeUrl == null) || eventTypeUrl.equals(event.typeUrl());
        return result;
    }

    /**
     * Tells if the passed message matches the filters.
     */
    private static boolean check(Message message, Collection<FieldFilter> filters) {
        for (var filter : filters) {
            var matchesFilter = checkFields(message, filter);
            if (!matchesFilter) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkFields(Message object, FieldFilter filter) {
        var field = fieldFrom(filter);
        var value = field.findValue(object);
        if (value.isEmpty()) {
            /* If there is no value in the field, return `true`
               when the list of required values is also empty. */
            var nothingIsExpected = filter.getValueList().isEmpty();
            return nothingIsExpected;
        }
        var expectedValues = filter.getValueList()
                .stream()
                .map(unpackFunc())
                .collect(toList());
        var msg = (Message) value.get();
        var result = expectedValues.contains(msg);
        return result;
    }

    /**
     * Obtains the last component from a potentially fully-qualified field path
     * from the passed filter.
     */
    private static Field fieldFrom(FieldFilter filter) {
        var path = filter.getFieldPath();
        var fieldName = path.substring(path.lastIndexOf('.') + 1);
        if (fieldName.isEmpty()) {
            throw newIllegalArgumentException(
                    "Unable to get a field name from the field filter: `%s`.", filter);
        }
        return Field.named(fieldName);
    }
}
