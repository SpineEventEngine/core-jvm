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

package io.spine.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.EntityState;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.client.OrderBy.Direction.OD_UNKNOWN;
import static io.spine.client.OrderBy.Direction.UNRECOGNIZED;

/**
 * A builder for the {@link Query} instances.
 *
 * <p>None of the parameters set by the builder methods are required. Call {@link #build()} to
 * retrieve the resulting {@link Query} instance.
 *
 * <p>Usage example:
 * <pre>{@code
 * ActorRequestFactory factory = ... ;
 * Query query = factory.query()
 *          .select(Customer.class)
 *          .byId(westCoastCustomerIds())
 *          .withMask("name", "address", "email")
 *          .where(eq("type", "permanent"),
 *                 eq("discountPercent", 10),
 *                 eq("companySize", Company.Size.SMALL))
 *          .orderBy("name", ASCENDING)
 *          .limit(20)
 *          .build();
 * }</pre>
 *
 * <p>Arguments for the {@link #where(Filter...)} method can be composed using the {@link Filters}
 * utility class.
 *
 * @see Filters
 * @see TargetBuilder
 */
public final class QueryBuilder extends TargetBuilder<Query, QueryBuilder> {

    private final QueryFactory queryFactory;

    private String orderingColumn;
    private OrderBy.Direction direction;
    private int limit = 0;

    QueryBuilder(Class<? extends EntityState<?>> targetType, QueryFactory queryFactory) {
        super(targetType);
        this.queryFactory = checkNotNull(queryFactory);
    }

    /**
     * Sets the sorting order by the target column and order direction.
     *
     * @param column
     *         the entity column to sort by
     * @param direction
     *         sorting direction
     */
    @CanIgnoreReturnValue
    public QueryBuilder orderBy(String column, OrderBy.Direction direction) {
        checkNotNull(column);
        checkNotNull(direction);
        checkArgument(
                direction != OD_UNKNOWN && direction != UNRECOGNIZED,
                "Invalid ordering direction"
        );

        this.orderingColumn = column;
        this.direction = direction;
        return self();
    }

    /**
     * Limits the number of results returned by the query.
     *
     * @param count
     *         the number of results to be returned
     */
    @CanIgnoreReturnValue
    public QueryBuilder limit(int count) {
        checkLimit(count);
        this.limit = count;
        return self();
    }

    /**
     * Ensures that the passed value is positive.
     *
     * @throws IllegalArgumentException
     *         if the value is zero or negative
     */
    private static void checkLimit(long count) {
        checkArgument(count > 0, "A query limit must be a positive value.");
    }

    /**
     * Generates a new {@link Query} instance with current builder configuration.
     *
     * @return the built {@link Query}
     */
    @Override
    @SuppressWarnings("OptionalIsPresent")  // For better readability.
    public Query build() {
        var orderBy = orderBy();
        var target = buildTarget();
        var mask = composeMask();

        if (limit > 0) {
            checkState(orderBy.isPresent(), "Limit cannot be set for unordered Queries.");
            return queryFactory.composeQuery(target, orderBy.get(), limit, mask);
        }
        if (orderBy.isPresent()) {
            return queryFactory.composeQuery(target, orderBy.get(), mask);
        }
        return queryFactory.composeQuery(target, mask);
    }

    private Optional<OrderBy> orderBy() {
        if (orderingColumn == null) {
            return Optional.empty();
        }
        var result = OrderBy.newBuilder()
                .setColumn(orderingColumn)
                .setDirection(direction)
                .build();
        return Optional.of(result);
    }

    @Override
    QueryBuilder self() {
        return this;
    }
}
