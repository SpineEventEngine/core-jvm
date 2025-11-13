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

import io.spine.base.EntityState;
import io.spine.client.Filter.Operator;
import io.spine.query.EntityStateField;

import java.io.Serial;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.GREATER_THAN;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_THAN;
import static io.spine.client.Filters.checkSupportedOrderingComparisonType;
import static io.spine.client.Filters.createFilter;

/**
 * A subscription filter which targets an {@link EntityState}.
 */
public final class EntityStateFilter extends TypedFilter<EntityState<?>> {

    @Serial
    private static final long serialVersionUID = 0L;

    private EntityStateFilter(EntityStateField field, Object expected, Operator operator) {
        super(createFilter(field.getField(), expected, operator));
    }

    /**
     * Creates a new equality filter.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EntityStateFilter eq(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return new EntityStateFilter(field, value, EQUAL);
    }

    /**
     * Creates a new "greater than" filter.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EntityStateFilter gt(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EntityStateFilter(field, value, GREATER_THAN);
    }

    /**
     * Creates a new "less than" filter.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EntityStateFilter lt(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EntityStateFilter(field, value, LESS_THAN);
    }

    /**
     * Creates a new "greater than or equals" filter.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EntityStateFilter ge(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EntityStateFilter(field, value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "less than or equals" filter.
     *
     * <p>NOTE: not all value types are supported for ordering comparison. See {@link Filters} for
     * details.
     *
     * @param field
     *         the message field from which the actual value is taken
     * @param value
     *         the expected value
     */
    public static EntityStateFilter le(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return new EntityStateFilter(field, value, LESS_OR_EQUAL);
    }
}
