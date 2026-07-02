/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.base.EventMessageField;
import io.spine.base.Field;
import io.spine.base.FieldPath;
import io.spine.client.CompositeFilter.CompositeOperator;
import io.spine.compare.ComparatorRegistry;
import io.spine.core.Event;
import io.spine.core.EventContextField;
import io.spine.core.Version;
import io.spine.query.Column;
import io.spine.query.ColumnName;
import io.spine.query.EntityStateField;

import java.util.Collection;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.asList;
import static io.spine.client.CompositeFilter.CompositeOperator.ALL;
import static io.spine.client.CompositeFilter.CompositeOperator.EITHER;
import static io.spine.client.Filter.Operator;
import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.GREATER_THAN;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_THAN;
import static io.spine.protobuf.TypeConverter.toAny;
import static java.util.Arrays.stream;

/**
 * A factory of {@link Filter} instances.
 *
 * <p>Public methods of this class represent the recommended way to create a {@link Filter}.
 * <a name="types"></a>
 * <h3>Supported Types</h3>
 *
 * <p>The filters allow to put criteria on fields with comparison operations. The criteria either
 * define the particular values for the fields or specify some ordering.
 *
 * <p>The {@linkplain #eq equality comparison} supports any data type for the compared objects.
 *
 * <p>The ordering comparison ({@link #gt &gt;}, {@link #lt &lt;}, {@link #ge &gt;=},
 * {@link #le &lt;=}) supports a value type that is either:
 * <ul>
 *     <li>{@link Comparable} — this covers Java primitive number types, {@code String}, and
 *         any message type marked with the {@code (compare_by)} option (e.g.
 *         {@link Version io.spine.core.Version} and the {@code io.spine.time} date/time types);
 *     <li>registered in the {@link io.spine.compare.ComparatorRegistry ComparatorRegistry} —
 *         this covers types such as {@link Timestamp com.google.protobuf.Timestamp} for which
 *         a {@link java.util.Comparator} is provided instead of implementing {@code Comparable}.
 * </ul>
 *
 * <p>Therefore, to make a custom column type usable in the ordering comparison, either mark its
 * message type with the {@code (compare_by)} option, or register a {@code Comparator} for it in
 * the {@code ComparatorRegistry}.
 *
 * @see QueryBuilder for the application
 * @see EntityStateFilter
 * @see EventFilter
 */
@SuppressWarnings("ClassWithTooManyMethods") // A lot of method overloads for filter creation.
public final class Filters {

    /** Prevents this utility class instantiation. */
    private Filters() {
    }

    /**
     * Creates a new equality {@link Filter}.
     *
     * @param fieldPath
     *         the field path or the entity column name for entity filters
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter eq(String fieldPath, Object value) {
        checkNotNull(fieldPath);
        checkNotNull(value);
        return createFilter(fieldPath, value, EQUAL);
    }

    /**
     * Creates a new equality {@link Filter}.
     *
     * @param column
     *         the column to filter by
     * @param value
     *         the requested value
     * @param <V>
     *         the type of column values
     * @return a new instance of {@code Filter}
     */
    public static <V> Filter eq(Column<?, V> column, V value) {
        checkNotNull(column);
        checkNotNull(value);
        return createFilter(column.name(), value, EQUAL);
    }

    /**
     * Creates a new equality {@link Filter}.
     *
     * @param field
     *         the entity state field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter eq(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, EQUAL);
    }

    /**
     * Creates a new equality {@link Filter}.
     *
     * @param field
     *         the event message field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter eq(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, EQUAL);
    }

    /**
     * Creates a new equality {@link Filter}.
     *
     * <p>The field path in the filter will be automatically prepended with {@code "context."} to
     * enable filtering by {@linkplain io.spine.core.EventContext event context}.
     *
     * @param field
     *         the event context field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter eq(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createContextFilter(field.getField(), value, EQUAL);
    }

    /**
     * Creates a new "greater than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param fieldPath
     *         the field path or the entity column name for entity filters
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter gt(String fieldPath, Object value) {
        checkNotNull(fieldPath);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return createFilter(fieldPath, value, GREATER_THAN);
    }

    /**
     * Creates a new "greater than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param column
     *         the entity column to filter by
     * @param value
     *         the requested value
     * @param <V>
     *         the type of column values
     * @return a new instance of {@code Filter}
     */
    public static <V> Filter gt(Column<?, V> column, V value) {
        checkNotNull(column);
        checkNotNull(value);
        return createFilter(column.name(), value, GREATER_THAN);
    }

    /**
     * Creates a new "greater than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param field
     *         the entity state field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter gt(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, GREATER_THAN);
    }

    /**
     * Creates a new "greater than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param field
     *         the event message field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter gt(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, GREATER_THAN);
    }

    /**
     * Creates a new "greater than" {@link Filter}.
     *
     * <p>The field path in the filter will be automatically prepended with {@code "context."} to
     * enable filtering by {@linkplain io.spine.core.EventContext event context}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param field
     *         the event context field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter gt(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createContextFilter(field.getField(), value, GREATER_THAN);
    }

    /**
     * Creates a new "less than" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param fieldPath
     *         the field path or the entity column name for entity filters
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter lt(String fieldPath, Object value) {
        checkNotNull(fieldPath);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return createFilter(fieldPath, value, LESS_THAN);
    }

    /**
     * Creates a new "less than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param column
     *         the entity column to filter by
     * @param value
     *         the requested value
     * @param <V>
     *         the type of column values
     * @return a new instance of {@code Filter}
     */
    public static <V> Filter lt(Column<?, V> column, V value) {
        checkNotNull(column);
        checkNotNull(value);
        return createFilter(column.name(), value, LESS_THAN);
    }

    /**
     * Creates a new "less than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param field
     *         the entity state field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter lt(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, LESS_THAN);
    }

    /**
     * Creates a new "less than" {@link Filter}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param field
     *         the event message field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter lt(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, LESS_THAN);
    }

    /**
     * Creates a new "less than" {@link Filter}.
     *
     * <p>The field path in the filter will be automatically prepended with {@code "context."} to
     * enable filtering by {@linkplain io.spine.core.EventContext event context}.
     *
     * <p>For the supported types description, see <a href="#types">Comparison types section</a>.
     *
     * @param field
     *         the event context field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter lt(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createContextFilter(field.getField(), value, LESS_THAN);
    }

    /**
     * Creates a new "greater or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param fieldPath
     *         the field path or the entity column name for entity filters
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter ge(String fieldPath, Object value) {
        checkNotNull(fieldPath);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return createFilter(fieldPath, value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "greater or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param column
     *         the entity column to filter by
     * @param value
     *         the requested value
     * @param <V>
     *         the type of column values
     * @return a new instance of {@code Filter}
     */
    public static <V> Filter ge(Column<?, V> column, V value) {
        checkNotNull(column);
        checkNotNull(value);
        return createFilter(column.name(), value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "greater or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param field
     *         the entity state field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter ge(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "greater or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param field
     *         the event message field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter ge(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "greater or equal" {@link Filter}.
     *
     * <p>The field path in the filter will be automatically prepended with {@code "context."} to
     * enable filtering by {@linkplain io.spine.core.EventContext event context}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param field
     *         the event context field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter ge(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createContextFilter(field.getField(), value, GREATER_OR_EQUAL);
    }

    /**
     * Creates a new "less or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param fieldPath
     *         the field path or the entity column name for entity filters
     * @param value
     *         the requested value
     * @return new instance of {@code Filter}
     */
    public static Filter le(String fieldPath, Object value) {
        checkNotNull(fieldPath);
        checkNotNull(value);
        checkSupportedOrderingComparisonType(value.getClass());
        return createFilter(fieldPath, value, LESS_OR_EQUAL);
    }

    /**
     * Creates a new "less or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param column
     *         the entity column to filter by
     * @param value
     *         the requested value
     * @param <V>
     *         the type of column values
     * @return a new instance of {@code Filter}
     */
    public static <V> Filter le(Column<?, V> column, V value) {
        checkNotNull(column);
        checkNotNull(value);
        return createFilter(column.name(), value, LESS_OR_EQUAL);
    }

    /**
     * Creates a new "less or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param field
     *         the entity state field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter le(EntityStateField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, LESS_OR_EQUAL);
    }

    /**
     * Creates a new "less or equal" {@link Filter}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param field
     *         the event message field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter le(EventMessageField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createFilter(field.getField(), value, LESS_OR_EQUAL);
    }

    /**
     * Creates a new "less or equal" {@link Filter}.
     *
     * <p>The field path in the filter will be automatically prepended with {@code "context."} to
     * enable filtering by {@linkplain io.spine.core.EventContext event context}.
     *
     * <p>See <a href="#types">Comparison types</a> section for the supported types description.
     *
     * @param field
     *         the event context field to filter by
     * @param value
     *         the requested value
     * @return a new instance of {@code Filter}
     */
    public static Filter le(EventContextField field, Object value) {
        checkNotNull(field);
        checkNotNull(value);
        return createContextFilter(field.getField(), value, LESS_OR_EQUAL);
    }

    /**
     * Creates a new conjunction composite filter.
     *
     * <p>A record is considered matching this filter if and only if it matches all of the
     * aggregated filters.
     *
     * @param first
     *         the first {@link Filter}
     * @param rest
     *         the array of additional {@linkplain Filter filters}, possibly empty
     * @return new instance of {@link CompositeFilter}
     */
    public static CompositeFilter all(Filter first, Filter... rest) {
        checkNotNull(first);
        checkNotNull(rest);
        return composeFilters(asList(first, rest), ALL);
    }

    /**
     * Creates a new disjunction composite filter.
     *
     * <p>A record is considered matching this filter if it matches at least one of the aggregated
     * filters.
     *
     * @param first
     *         the first {@link Filter}
     * @param rest
     *         the array of additional {@linkplain Filter filters}, possibly empty
     * @return new instance of {@link CompositeFilter}
     */
    public static CompositeFilter either(Filter first, Filter... rest) {
        checkNotNull(first);
        checkNotNull(rest);
        return composeFilters(asList(first, rest), EITHER);
    }

    /**
     * Creates a new composite filter with a disjunction applied to the aggregated filters.
     *
     * <p>A record is considered matching this filter if it matches at least one of
     * the aggregated filters.
     *
     * <p>This method is used to create the default {@code EITHER} filter if the user
     * chooses to pass instances of {@link Filter} directly to the {@link QueryBuilder}.
     *
     * @param filters
     *         the aggregated filters
     * @return new instance of {@link CompositeFilter}
     * @see #either(Filter, Filter...) for the public API equivalent
     */
    static CompositeFilter either(Collection<Filter> filters) {
        checkNotNull(filters);
        checkFiltersNotEmpty(filters);
        return composeFilters(filters, EITHER);
    }

    /**
     * Creates a new conjunction composite filter.
     *
     * <p>A record is considered matching this filter if and only if it matches all of
     * the aggregated filters.
     *
     * <p>This method is used to create the default {@code ALL} filter if the user chooses to pass
     * instances of {@link Filter} directly to the {@link QueryBuilder}.
     *
     * @param filters
     *         the aggregated filters
     * @return new instance of {@code CompositeFilter}
     * @see #all(Filter, Filter...) for the public API equivalent
     */
    static CompositeFilter all(Collection<Filter> filters) {
        checkNotNull(filters);
        checkFiltersNotEmpty(filters);
        return composeFilters(filters, ALL);
    }

    private static void checkFiltersNotEmpty(Collection<Filter> filters) {
        checkArgument(!filters.isEmpty(),
                      "Composite filter must contain at least one plain filter in it.");
    }

    /**
     * Creates a new filter that targets a certain column by its name comparing the actual
     * column values to the one passed value according to the passed operator.
     *
     * @param columnName
     *         the name of the column
     * @param value
     *         the value to filter by
     * @param operator
     *         the operator to use in comparison of actual values to the passed one
     * @return a new filter instance
     */
    static Filter createFilter(ColumnName columnName, Object value, Operator operator) {
        var fieldPath = columnName.value();
        return createFilter(fieldPath, value, operator);
    }

    /**
     * Creates a new filter that targets a certain {@code Message} field by comparing the actual
     * field values to the one passed value according to the passed operator.
     *
     * @param field
     *         the declaration of a field in a {@code Message} instances of which are filtered
     * @param value
     *         the value to filter by
     * @param operator
     *         the operator to use in comparison of actual values to the passed one
     * @return a new filter instance
     */
    static Filter createFilter(Field field, Object value, Operator operator) {
        checkNotNull(field);
        var fieldPath = field.path();
        return createFilter(fieldPath, value, operator);
    }

    /**
     * Creates a filter for {@link Event} instances that is applied to one of the fields in
     * the {@link io.spine.core.EventContext EventContext} corresponding to the {@code Event}.
     *
     * @param field
     *         the declaration of the field in the {@code EventContext}
     * @param value
     *         the value to filter by
     * @param operator
     *         the operator to use in filtering
     * @return a new instance of {@code Filter}
     */
    static Filter createContextFilter(Field field, Object value, Operator operator) {
        checkNotNull(field);
        checkNotNull(value);
        checkNotNull(operator);
        var fieldPath = Event.Field.context()
                                   .getField()
                                   .nested(field)
                                   .path();
        return createFilter(fieldPath, value, operator);
    }

    /**
     * Creates a new composite filter out of the passed {@code Filter} instances joining them
     * with the passed composite operator.
     *
     * @param filters
     *         the filters to include into the composite filter
     * @param operator
     *         the operator to use in evaluation of the intermediate evaluation results
     *         of each of the aggregated filters
     * @return a new instance of {@code CompositeFilter}
     */
    static CompositeFilter composeFilters(Iterable<Filter> filters, CompositeOperator operator) {
        checkNotNull(filters);
        checkNotNull(operator);
        var result = CompositeFilter.newBuilder()
                .addAllFilter(filters)
                .setOperator(operator)
                .build();
        return result;
    }

    /**
     * Obtains the {@link Filter}s from the passed array of {@code Filter} wrappers and returns
     * them as new array.
     *
     * @param filters
     *         the filter wrappers
     * @return a new array of the {@code Filter} instances
     */
    static Filter[] extractFilters(TypedFilter<?>[] filters) {
        checkNotNull(filters);
        return stream(filters)
                .map(TypedFilter::filter)
                .toArray(Filter[]::new);
    }

    /**
     * Obtains the {@link Filter}s from the passed collection of {@code Filter} wrappers and returns
     * them as a new immutable list.
     *
     * @param filters
     *         the filter wrappers
     * @param <M>
     *         the type of the filtered messages specific to the passed filter wrappers
     * @return an immutable list of the {@code Filter}s
     */
    static <M extends Message> ImmutableList<Filter>
    extractFilters(Collection<? extends TypedFilter<M>> filters) {
        checkNotNull(filters);
        return filters.stream()
                      .map(TypedFilter::filter)
                      .collect(toImmutableList());
    }

    /**
     * Obtains the {@link CompositeFilter}s from the passed array of {@code CompositeFilter}
     * wrappers and returns them as new array.
     *
     * @param filters
     *         the composite filter wrappers
     * @return a new array of the {@code CompositeFilter} instances
     */
    static CompositeFilter[] extractFilters(TypedCompositeFilter<?>[] filters) {
        checkNotNull(filters);
        return stream(filters)
                .map(TypedCompositeFilter::filter)
                .toArray(CompositeFilter[]::new);
    }

    /**
     * Ensures that the instances of the passed type are supported for ordering comparison.
     *
     * <p>A type qualifies if it is {@link Comparable} (for a message type, this is achieved with
     * the {@code (compare_by)} option) or has a {@link java.util.Comparator Comparator} registered
     * in the {@link io.spine.compare.ComparatorRegistry ComparatorRegistry}.
     *
     * @param cls
     *         the type to check
     * @throws IllegalArgumentException
     *         if the passed type does not qualify for the ordering comparison
     */
    static void checkSupportedOrderingComparisonType(Class<?> cls) {
        checkNotNull(cls);
        var dataType = Primitives.wrap(cls);
        var supported = Comparable.class.isAssignableFrom(dataType)
                || ComparatorRegistry.contains(dataType);
        checkArgument(supported,
                      "The type `%s` is not supported for the ordering comparison. A type "
                              + "qualifies if it is `Comparable` (for a message, apply the "
                              + "`(compare_by)` option) or has a comparator registered in "
                              + "`io.spine.compare.ComparatorRegistry`.",
                      dataType.getCanonicalName());
    }

    /**
     * Creates a filter of events that can apply conditions from the passed
     * {@code CompositeFilter} to both event message and its context.
     *
     * <p>The filter is deemed addressing the {@linkplain io.spine.core.EventContext event context}
     * if the field path specified in it starts with the {@code "context."} prefix.
     */
    @Internal
    public static Predicate<Event> toEventFilter(CompositeFilter filterData) {
        checkNotNull(filterData);
        return new CompositeEventFilter(filterData);
    }

    private static Filter createFilter(FieldPath path, Object value, Operator operator) {
        checkNotNull(path);
        checkNotNull(value);
        checkNotNull(operator);
        var wrappedValue = toAny(value);
        var filter = Filter.newBuilder()
                .setFieldPath(path)
                .setValue(wrappedValue)
                .setOperator(operator)
                .build();
        return filter;
    }

    private static Filter createFilter(String fieldPath, Object value, Operator operator) {
        var field = Field.parse(fieldPath);
        return createFilter(field, value, operator);
    }
}
