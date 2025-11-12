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

package io.spine.server.storage;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import io.spine.base.Identifier;
import io.spine.client.CompositeFilter;
import io.spine.client.Filter;
import io.spine.client.IdFilter;
import io.spine.client.OrderBy;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.protobuf.TypeConverter;
import io.spine.query.Column;
import io.spine.query.ColumnName;
import io.spine.query.Either;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.join;
import static java.util.stream.Collectors.toList;

/**
 * Converts the queries defined in Protobuf into the language of {@code io.spine.query} package.
 */
public final class QueryConverter {

    private QueryConverter() {
    }

    /**
     * Converts the Protobuf-based filters and the response format into a {@code RecordQuery}.
     *
     * @param <I>
     *         the type of the record identifiers
     * @param <R>
     *         the type of the records which are queried
     * @param filters
     *         the original Protobuf-based filters to convert
     * @param format
     *         the original response format to convert
     * @param spec
     *         the specification of the columns stored for the particular record type
     * @return a new record query with the same semantic as the original filters and response format
     */
    public static <I, R extends Message> RecordQuery<I, R>
    convert(TargetFilters filters, ResponseFormat format, RecordSpec<I, R> spec) {
        checkNotNull(spec);
        checkNotNull(filters);
        checkNotNull(format);

        var idType = spec.idType();
        var recordType = spec.recordType();
        var builder = RecordQuery.newBuilder(idType, recordType);

        identifiers(builder, filters.getIdFilter());
        filters(builder, spec, filters);
        fieldMask(builder, format);
        orderByAndLimit(builder, spec, format);

        return builder.build();
    }

    /**
     * Creates a new {@code RecordQuery} based on the given response format and
     * the storage specification of the queried record.
     *
     * <p>The result contains no filters on any record field.
     *
     * @param spec
     *         the specification of the columns stored for the particular record type
     * @param format
     *         the original response format
     * @param <I>
     *         the type of the record identifiers
     * @param <R>
     *         the type of the records which are queried
     * @return a new record query
     */
    public static <I, R extends Message> RecordQuery<I, R>
    newQuery(RecordSpec<I, R> spec, ResponseFormat format) {
        checkNotNull(spec);
        checkNotNull(format);

        var idType = spec.idType();
        var recordType = spec.recordType();
        var builder = RecordQuery.newBuilder(idType, recordType);
        fieldMask(builder, format);
        orderByAndLimit(builder, spec, format);
        return builder.build();
    }

    private static <I, R extends Message> void
    filters(RecordQueryBuilder<I, R> builder, RecordSpec<I, R> spec, TargetFilters filters) {
        for (var composite : filters.getFilterList()) {
            convertComposite(composite, builder, spec);
        }
    }

    private static <I, R extends Message> void
    convertComposite(CompositeFilter composite,
                     RecordQueryBuilder<I, R> builder,
                     RecordSpec<I, R> spec) {
        var compositeOperator = composite.getOperator();

        switch (compositeOperator) {
            case ALL:
                doCompositeWithAnd(composite, builder, spec);
                break;
            case EITHER:
                doCompositeWithOr(composite, builder, spec);
                break;
            case UNRECOGNIZED:
            case CCF_CO_UNDEFINED:
            default:
                throw newIllegalArgumentException(
                        "Unsupported composite operator `%s` encountered.",
                        compositeOperator);
        }
    }

    /**
     * Converts the parts of the passed composite filter (i.e. both its simple filters
     * and child composite filters) into the conditions and applies them to the passed builder
     * in accordance with the record specification.
     *
     * <p>This method works for conjunctive composite filters
     */
    @CanIgnoreReturnValue
    @SuppressWarnings("MethodWithMultipleLoops")    /* To avoid a myriad of tiny static methods. */
    private static <I, R extends Message> RecordQueryBuilder<I, R>
    doCompositeWithAnd(CompositeFilter filter,
                       RecordQueryBuilder<I, R> builder,
                       RecordSpec<I, R> spec) {
        for (var childFilter : filter.getFilterList()) {
            doSimpleFilter(childFilter, builder, spec);
        }
        var childComposites = filter.getCompositeFilterList();
        for (var child : childComposites) {
            convertComposite(child, builder, spec);
        }

        return builder;
    }

    /**
     * Converts the parts of the passed composite filter (i.e. both its simple filters
     * and child composite filters) into the conditions and applies them to the passed builder
     * in accordance with the record specification.
     *
     * <p>This method works for disjunctive composite filters
     */
    @CanIgnoreReturnValue
    private static <I, R extends Message> RecordQueryBuilder<I, R>
    doCompositeWithOr(CompositeFilter filter,
                       RecordQueryBuilder<I, R> builder,
                       RecordSpec<I, R> spec) {
        ImmutableList.Builder<Either<RecordQueryBuilder<I, R>>> eithers =
                ImmutableList.builder();

        var simpleEithers =
                simpleFiltersToEither(filter.getFilterList(), spec);
        var compositeEithers =
                compositesToEither(filter.getCompositeFilterList(), spec);

        var result =
                eithers.addAll(simpleEithers)
                       .addAll(compositeEithers)
                       .build();
        builder.either(result);

        return builder;
    }

    /**
     * Converts a bunch of simple {@code Filter}s to a distinct {@link Either} statements.
     */
    @SuppressWarnings("Immutable")  /* Using `builder` in lambda is fine. */
    private static <I, R extends Message> ImmutableList<Either<RecordQueryBuilder<I, R>>>
    simpleFiltersToEither(List<Filter> simpleFilters, RecordSpec<I, R> spec) {
        return simpleFilters
                .stream()
                .map((filter) ->
                             (Either<RecordQueryBuilder<I, R>>) localBuilder -> {
                                 doSimpleFilter(filter, localBuilder, spec);
                                 return localBuilder;
                             }
                )
                .collect(toImmutableList());
    }

    /**
     * Converts a list of composite filters to a distinct {@link Either} statements.
     *
     * <p>The children of each composite are also processed.
     */
    @SuppressWarnings("Immutable")  /* Using `builder` in lambda is fine. */
    private static <I, R extends Message> ImmutableList<Either<RecordQueryBuilder<I, R>>>
    compositesToEither(List<CompositeFilter> composites, RecordSpec<I, R> spec) {
        return composites
                .stream()
                .map((composite) ->
                             (Either<RecordQueryBuilder<I, R>>) localBuilder -> {
                                 convertComposite(composite, localBuilder, spec);
                                 return localBuilder;
                             }
                )
                .collect(toImmutableList());
    }

    /**
     * Converts a single filter into a condition and adds it to the passed builder
     * according to the record specification.
     */
    private static <I, R extends Message> void
    doSimpleFilter(Filter filter, RecordQueryBuilder<I, R> builder, RecordSpec<I, R> spec) {
        var name = columnNameOf(filter);
        var column = findColumn(spec, name);
        var convertedColumn = new AsRecordColumn<R>(column);

        var value = TypeConverter.toObject(filter.getValue(), column.type());

        var operator = filter.getOperator();
        switch (operator) {
            case EQUAL:
                builder.where(convertedColumn).is(value);
                break;
            case GREATER_THAN:
                builder.where(convertedColumn).isGreaterThan(value);
                break;
            case GREATER_OR_EQUAL:
                builder.where(convertedColumn).isGreaterOrEqualTo(value);
                break;
            case LESS_THAN:
                builder.where(convertedColumn).isLessThan(value);
                break;
            case LESS_OR_EQUAL:
                builder.where(convertedColumn).isLessOrEqualTo(value);
                break;

            case UNRECOGNIZED:
            case CFO_UNDEFINED:
            default:
                throw newIllegalArgumentException("Unsupported filter operator `%s` encountered.",
                                                  operator);
        }
    }

    @SuppressWarnings("unchecked")
    private static <I, R extends Message>
    void identifiers(RecordQueryBuilder<I, R> builder, IdFilter idFilter) {
        if (idFilter.getIdCount() > 0) {
            var ids = idFilter.getIdList()
                    .stream()
                    .map(Identifier::unpack)
                    .map(id -> (I) id)
                    .collect(toList());
            builder.id().in(ids);
        }
    }

    private static <I, R extends Message>
    void fieldMask(RecordQueryBuilder<I, R> builder, ResponseFormat format) {
        if (format.hasFieldMask()) {
            builder.withMask(format.getFieldMask());
        }
    }

    private static <I, R extends Message>
    void orderByAndLimit(RecordQueryBuilder<I, R> builder,
                         RecordSpec<I, R> spec,
                         ResponseFormat format) {
        var hasOrderBy = false;
        for (var protoOrderBy : format.getOrderByList()) {
            hasOrderBy = true;
            var columnName = ColumnName.of(protoOrderBy.getColumn());
            var protoDirection = protoOrderBy.getDirection();

            var column = findColumn(spec, columnName);
            var convertedColumn = new AsRecordColumn<R>(column);
            if (protoDirection == OrderBy.Direction.ASCENDING) {
                builder.sortAscendingBy(convertedColumn);
            } else {
                builder.sortDescendingBy(convertedColumn);
            }
        }

        var limit = format.getLimit();
        if (limit > 0) {
            checkArgument(hasOrderBy, "Storage query must have at least one ordering directive " +
                    "if the limit is set.");
            builder.limit(limit);
        }
    }

    private static <I, R extends Message> Column<?, ?>
    findColumn(RecordSpec<I, R> spec, ColumnName columnName) {
        var maybeColumn = spec.findColumn(columnName);
        checkArgument(maybeColumn.isPresent(),
                      "Cannot find the column `%s` for the type `%s`.",
                      columnName, spec.recordType());
        var column = maybeColumn.get();
        return column;
    }

    private static ColumnName columnNameOf(Filter filter) {
        var fieldPath = filter.getFieldPath();
        checkArgument(fieldPath.getFieldNameCount() == 1,
                      "Incorrect Column name in Entity Filter: `%s`.",
                      join(".", fieldPath.getFieldNameList()));
        var column = fieldPath.getFieldName(0);
        return ColumnName.of(column);
    }

    /**
     * A view on a {@link Column} as on a {@link RecordColumn}.
     *
     * <p>Serves for the column conversion when a simple cast is not possible due to the generic
     * type erasure.
     *
     * @param <R>
     *         the type of the message which column is being viewed
     */
    private static final class AsRecordColumn<R extends Message> extends RecordColumn<R, Object> {

        private static final long serialVersionUID = 0L;

        private AsRecordColumn(Column<?, ?> origin) {
            super(origin.name().value(), Object.class, new NoGetter<>());
        }
    }

    /**
     * Returns the getter which always throws an {@link IllegalStateException} upon invocation.
     *
     * @param <R>
     *         the type of the message which column getter it is
     */
    @Immutable
    private static final class NoGetter<R extends Message> implements Column.Getter<R, Object> {

        @Override
        public Object apply(R r) {
            throw newIllegalStateException(
                    "`AsRecordColumn`s serve for the column conversion only " +
                            "and do not provide a getter.");
        }
    }
}
