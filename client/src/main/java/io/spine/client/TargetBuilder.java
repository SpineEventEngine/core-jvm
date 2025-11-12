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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.FieldMaskUtil.fromStringList;
import static io.spine.client.Filters.all;
import static io.spine.client.Targets.composeTarget;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

/**
 * An abstract base for builders that create {@link com.google.protobuf.Message Message instances}
 * which have a {@link Target} and a {@link FieldMask} as attributes.
 *
 * <p>The {@link Target} matching the builder configuration is created with {@link #buildTarget()},
 * while the {@link FieldMask} is composed with {@link #composeMask()}.
 *
 * <p>The public API of this class is inspired by SQL syntax:
 * <pre>{@code
 *     select(Customer.class) // returning <AbstractTargetBuilder> descendant instance
 *         .byId(westCoastCustomerIds())
 *         .withMask("name", "address", "email")
 *         .where(eq("type", "permanent"),
 *                eq("discountPercent", 10),
 *                eq("companySize", Company.Size.SMALL))
 *         .build();
 * }</pre>
 *
 * <p>Calling any of the builder methods overrides the previous call of the given method or
 * any of its overloads. For example, calling sequentially:
 * <pre>{@code
 *     builder.withMask(mask1)
 *            .withMask(mask2)
 *            // optionally some other invocations
 *            .withMask(mask3)
 *            .build();
 * }</pre>
 * is equivalent to calling:
 * <pre>{@code
 *     builder.withMask(mask3)
 *            .build();
 *     }
 * </pre>
 *
 * @param <T>
 *         a type of the message which is returned by the implementations {@link #build()}
 * @param <B>
 *         a type of the builder implementations
 */
public abstract class TargetBuilder<T extends Message, B extends TargetBuilder<T, B>> {

    private final Class<? extends Message> targetType;

    /*
        All the optional fields are initialized only when and if set.
        The empty collections make effectively no influence, but null values allow us to create
        the query `Target` more efficiently.
     */

    private @Nullable Set<?> ids;
    private @Nullable Set<CompositeFilter> filters;
    private @Nullable Set<String> fieldMask;

    TargetBuilder(Class<? extends Message> targetType) {
        this.targetType = checkNotNull(targetType);
    }

    /**
     * Creates a new {@link io.spine.client.Target Target} based on the current builder
     * configuration.
     *
     * @return a new {@link io.spine.client.Query Query} or {@link io.spine.client.Topic Topic}
     *         target
     */
    Target buildTarget() {
        return composeTarget(targetType, ids, filters);
    }

    @Nullable FieldMask composeMask() {
        if (fieldMask == null || fieldMask.isEmpty()) {
            return null;
        }
        var mask = fromStringList(fieldMask);
        return mask;
    }

    /**
     * Sets the ID predicate to the targets of the request.
     *
     * <p>Though it's not prohibited at compile-time, please make sure to pass instances of the
     * same type to the argument of this method. Moreover, the instances must be of the type of
     * the query target type identifier.
     *
     * <p>This method or any of its overloads do not check these constraints and assume they are
     * followed by the caller.
     *
     * <p>If there are no IDs (i.e. and empty {@link Iterable} is passed), the query retrieves all
     * the records regardless of their IDs.
     *
     * @param ids
     *         the values of the IDs to look up
     * @return self for method chaining
     */
    @CanIgnoreReturnValue
    public B byId(Iterable<?> ids) {
        checkNotNull(ids);
        this.ids = ImmutableSet.copyOf(ids);
        return self();
    }

    /**
     * Sets the ID predicate to the {@link io.spine.client.Query}.
     *
     * @param ids
     *         the values of the IDs to look up
     * @return self for method chaining
     * @see #byId(Iterable)
     */
    @CanIgnoreReturnValue
    public B byId(Message... ids) {
        this.ids = ImmutableSet.copyOf(ids);
        return self();
    }

    /**
     * Sets the ID predicate to the {@link io.spine.client.Query}.
     *
     * @param ids
     *         the values of the IDs to look up
     * @return self for method chaining
     * @see #byId(Iterable)
     */
    @CanIgnoreReturnValue
    public B byId(String... ids) {
        this.ids = ImmutableSet.copyOf(ids);
        return self();
    }

    /**
     * Sets the ID predicate to the {@link io.spine.client.Query}.
     *
     * @param ids
     *         the values of the IDs to look up
     * @return self for method chaining
     * @see #byId(Iterable)
     */
    @CanIgnoreReturnValue
    public B byId(Integer... ids) {
        this.ids = ImmutableSet.copyOf(ids);
        return self();
    }

    /**
     * Sets the ID predicate to the {@link io.spine.client.Query}.
     *
     * @param ids
     *         the values of the IDs to look up
     * @return self for method chaining
     * @see #byId(Iterable)
     */
    @CanIgnoreReturnValue
    public B byId(Long... ids) {
        this.ids = ImmutableSet.copyOf(ids);
        return self();
    }

    /**
     * Sets the predicates for the {@link io.spine.client.Query}.
     *
     * <p>If there are no {@link io.spine.client.Filter}s (i.e. the passed array is empty),
     * all the records will be retrieved regardless of the column values.
     *
     * <p>The multiple parameters passed into this method are considered to be joined in
     * a conjunction ({@link io.spine.client.CompositeFilter.CompositeOperator#ALL ALL}
     * operator), i.e., a record matches this query only if it matches all of these parameters.
     *
     * @param predicate
     *         the {@link Filter io.spine.client.Filter}s to filter the query results
     * @return {@code this} for method chaining
     * @see Filters for a convenient way to create {@code Filter} instances
     * @see #where(io.spine.client.CompositeFilter...)
     */
    @CanIgnoreReturnValue
    public B where(Filter... predicate) {
        var aggregatingFilter = all(asList(predicate));
        filters = singleton(aggregatingFilter);
        return self();
    }

    /**
     * Sets the predicates for the {@link io.spine.client.Query}.
     *
     * <p>If there are no {@link io.spine.client.Filter}s (i.e. the passed array is empty),
     * all the records will be retrieved regardless the column values.
     *
     * <p>The input values represent groups of {@linkplain io.spine.client.Filter simple
     * filters} joined with
     * a {@linkplain io.spine.client.CompositeFilter.CompositeOperator composite operator}.
     *
     * <p>The input filter groups are effectively joined between each other by
     * {@link io.spine.client.CompositeFilter.CompositeOperator#ALL ALL} operator, i.e. a
     * record matches
     * this query if it matches all the composite filters.
     *
     * <p>Example of usage:
     * <pre>{@code
     *     factory.select(Customer.class)
     *            // Possibly other parameters
     *            .where(all(ge("companySize", 50), le("companySize", 1000)),
     *                   either(gt("establishedTime", twoYearsAgo), eq("country", "Germany")))
     *            .build();
     * }</pre>
     *
     * <p>In the example above, the {@code Customer} records match the built query if they
     * represent
     * companies that have their company size between 50 and 1000 employees and either have been
     * established less than two years ago, or originate from Germany.
     *
     * <p>Note that the filters which belong to different {@link Filters#all all(...)} groups
     * may be represented as a single {@link Filters#all all(...)} group. For example, the two
     * following queries would be identical:
     * <pre>{@code
     *     // Option 1
     *     factory.select(Customer.class)
     *            .where(all(
     *                       eq("country", "Germany")),
     *                   all(
     *                       ge("companySize", 50),
     *                       le("companySize", 100)))
     *            .build();
     *
     *     // Option 2 (identical)
     *     factory.select(Customer.class)
     *            .where(all(
     *                       eq("country", "Germany"),
     *                       ge("companySize", 50),
     *                       le("companySize", 100)))
     *            .build();
     * }</pre>
     *
     * <p>The {@code Option 1} is recommended in this case, since the filters are grouped
     * logically,
     * though both builders produce effectively the same {@link io.spine.client.Query} instances.
     * Please note that those instances may not be equal in terms of
     * the {@link Object#equals(Object)} method.
     *
     * @param predicate
     *         a number of {@link CompositeFilter io.spine.client.CompositeFilter} instances
     *         forming the query predicate
     * @return {@code this} for method chaining
     * @see Filters for a convenient way to create {@code CompositeFilter} instances
     */
    @CanIgnoreReturnValue
    public B where(CompositeFilter... predicate) {
        filters = ImmutableSet.copyOf(predicate);
        return self();
    }

    /**
     * Sets the entity fields to retrieve.
     *
     * <p>The names of the fields must be formatted according to the {@link FieldMask}
     * specification.
     *
     * <p>If there are no fields (i.e. an empty {@link Iterable} is passed), all the fields will
     * be retrieved.
     *
     * @param fieldNames
     *         the fields to query
     * @return self for method chaining
     */
    @CanIgnoreReturnValue
    public B withMask(Iterable<String> fieldNames) {
        checkNotNull(fieldNames);
        this.fieldMask = ImmutableSet.copyOf(fieldNames);
        return self();
    }

    /**
     * Sets the entity fields to retrieve.
     *
     * <p>The names of the fields must be formatted according to the {@link FieldMask}
     * specification.
     *
     * <p>If there are no fields (i.e. an empty array is passed), all the fields will
     * be retrieved.
     *
     * @param fieldNames
     *         the fields to query
     * @return self for method chaining
     */
    @CanIgnoreReturnValue
    public B withMask(String... fieldNames) {
        this.fieldMask = ImmutableSet.<String>builder()
                .add(fieldNames)
                .build();
        return self();
    }

    public abstract T build();

    @Override
    public String toString() {
        return queryString();
    }

    @SuppressWarnings("MethodWithMoreThanThreeNegations")
    // OK for this method as it's used primarily for debugging
    private String queryString() {
        var valueSeparator = "; ";
        var sb = new StringBuilder();

        @SuppressWarnings("unchecked") // Ensured by declaration of this class.
        var builderCls = (Class<B>) self().getClass();
        sb.append(builderCls.getSimpleName())
          .append('(')
          .append("SELECT ")
          .append(fieldMask == null || fieldMask.isEmpty() ? '*' : fieldMask)
          .append(" FROM ")
          .append(targetType.getSimpleName())
          .append(" WHERE (");

        if (ids != null && !ids.isEmpty()) {
            sb.append("id IN ")
              .append(ids)
              .append(valueSeparator);
        }

        if (filters != null && !filters.isEmpty()) {
            sb.append("AND filters: ")
              .append(filters);
        }

        sb.append(");");
        return sb.toString();
    }

    /**
     * A typed instance of current Builder.
     *
     * @return {@code this} with the required compile-time type
     */
    abstract B self();
}
