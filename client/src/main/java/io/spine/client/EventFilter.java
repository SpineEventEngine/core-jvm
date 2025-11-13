/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.client;

import io.spine.base.EventMessageField;
import io.spine.core.Event;
import io.spine.core.EventContextField;

import java.io.Serial;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.GREATER_THAN;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_THAN;
import static io.spine.client.Filters.checkSupportedOrderingComparisonType;
import static io.spine.client.Filters.createContextFilter;
import static io.spine.client.Filters.createFilter;

/**
 * A subscription filter which targets an {@link Event}.
 *
 * <p>Can filter events by conditions on both message and context. See factory methods of the
 * class for details.
 */
public final class EventFilter extends TypedFilter<Event> {

    @Serial
    private static final long serialVersionUID = 0L;

    private final boolean byContext;

    private EventFilter(Filter filter, boolean byContext) {
        super(filter);
        this.byContext = byContext;
    }

    private EventFilter(EventMessageField field, Object expected, Filter.Operator operator) {
        this(createFilter(field.getField(), expected, operator), false);
    }

    private EventFilter(EventContextField field, Object expected, Filter.Operator operator) {
        this(createContextFilter(field.getField(), expected, operator), true);
    }

    /**
     * Creates an instance from the passed {@code Filter} message.
     *
     * <p>The filter is considered targeting event context if the field path in the filter starts
     * with {@code "context."}.
     */
    EventFilter(Filter filter) {
        this(filter, isContextFilter(filter));
    }

    /**
     * Creates a new equality filter which targets a field in the event message.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter eq(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return new EventFilter(field, value, EQUAL);
    }

    /**
     * Creates a new equality filter which targets a field in the event context.
     *
     * @param field
     *         the context field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter eq(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return new EventFilter(field, value, EQUAL);
    }

    /**
     * Creates a new "greater than" filter which targets a field in the event message.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter gt(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, GREATER_THAN);
    }

    /**
     * Creates a new "greater than" filter which targets a field in the event context.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the context field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter gt(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, GREATER_THAN);
    }

    /**
     * Creates a new "less than" filter which targets a field in the event message.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter lt(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, LESS_THAN);
    }

    /**
     * Creates a new "less than" filter which targets a field in the event context.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the context field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter lt(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, LESS_THAN);
    }

    /**
     * Creates a new "greater than or equals" filter which targets a field in the event message.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter ge(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "greater than or equals" filter which targets a field in the event context.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the context field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter ge(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "less than or equals" filter which targets a field in the event message.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter le(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, LESS_OR_EQUAL);
    }

    /**
     * Creates a new "less than or equals" filter which targets a field in the event context.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the context field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EventFilter le(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EventFilter(field, value, LESS_OR_EQUAL);
    }

    @Override
    public boolean test(Event event) {
        if (byContext) {
            // Since we reference the context field with `context` prefix, we need to pass
            // the whole `Event` instance.
            return filter().test(event);
        }
        return filter().test(event.enclosedMessage());
    }

    private static boolean isContextFilter(Filter filter) {
        var contextFieldName = Event.Field.context()
                                          .getField()
                                          .toString();
        var firstInPath = filter.getFieldPath()
                                .getFieldName(0);
        var result = contextFieldName.equals(firstInPath);
        return result;
    }
}
