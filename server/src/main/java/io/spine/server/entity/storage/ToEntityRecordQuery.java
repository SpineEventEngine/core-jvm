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

package io.spine.server.entity.storage;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.query.Column;
import io.spine.query.ComparisonOperator;
import io.spine.query.Either;
import io.spine.query.EntityQuery;
import io.spine.query.LogicalOperator;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;
import io.spine.query.Subject;
import io.spine.query.SubjectParameter;
import io.spine.server.entity.EntityRecord;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.query.Direction.ASC;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Transforms the given {@link EntityQuery} into a {@link RecordQuery}
 * over the {@link EntityRecord}s.
 *
 * @param <I>
 *         the type of the identifiers of entities which are queried by the given query
 * @param <S>
 *         the type of the queried entity state
 */
@Internal
public final class ToEntityRecordQuery<I, S extends EntityState<I>>
        extends RecordQueryBuilder<I, EntityRecord> {

    /**
     * Transforms the entity query into a {@code RecordQuery}.
     *
     * @param query
     *         a query to transform
     * @param <I>
     *         the type of the identifiers of entities which are queried by the given query
     * @param <S>
     *         the type of the queried entity state
     * @return a new instance of {@code RecordQuery} targeting the {@link EntityRecord}s
     */
    public static <I, S extends EntityState<I>> RecordQuery<I, EntityRecord>
    transform(EntityQuery<I, S, ?> query) {
        checkNotNull(query);
        return new ToEntityRecordQuery<>(query).build();
    }

    private ToEntityRecordQuery(EntityQuery<I, S, ?> source) {
        super(source.subject()
                    .idType(), EntityRecord.class);
        var subject = source.subject();
        this.setIdParameter(subject.id());
        copyPredicates(subject);
        copySorting(source);
        copyLimit(source);
        copyMask(source);
    }

    /**
     * Copies the field masking setting from the passed {@code EntityQuery} to the current instance
     * of {@code ToEntityRecordQuery}.
     */
    private void copyMask(EntityQuery<I, S, ?> source) {
        var mask = source.mask();
        if (mask != null) {
            this.withMask(mask);
        }
    }

    /**
     * Copies the limit setting from the passed {@code EntityQuery} to the current instance
     * of {@code ToEntityRecordQuery}.
     */
    private void copyLimit(EntityQuery<I, S, ?> source) {
        var limit = source.limit();
        if (limit != null) {
            this.limit(limit);
        }
    }

    /**
     * Copies the sorting directives from the passed {@code EntityQuery} to the current instance
     * of {@code ToEntityRecordQuery}.
     */
    private void copySorting(EntityQuery<I, S, ?> source) {
        var sorting = source.sorting();
        for (var sortByOrigin : sorting) {
            RecordColumn<EntityRecord, ?> thisColumn =
                    AsEntityRecordColumn.apply(sortByOrigin.column());
            var direction = sortByOrigin.direction();
            if (ASC == direction) {
                this.sortAscendingBy(thisColumn);
            } else {
                this.sortDescendingBy(thisColumn);
            }
        }
    }

    /**
     * Copies the predicates of the passed subject to this instance of {@code ToEntityRecordQuery}.
     *
     * @param subject
     *         the subject which predicates to copy
     */
    private void copyPredicates(Subject<I, S> subject) {
        var root = subject.predicate();
        copyDescendants(this, root);

        var children = root.children();
        for (var child : children) {
            copyDescendants(this, child);
        }
    }

    /**
     * Copies both parameters and child predicates of the given {@code sourcePredicate}
     * into the passed {@code builder}.
     *
     * @param builder
     *         the builder to copy the descendants to
     * @param sourcePredicate
     *         query predicate to copy descendants from
     * @return the same instance of {@code builder}, for call chaining
     */
    @CanIgnoreReturnValue
    @SuppressWarnings("ResultOfMethodCallIgnored")  /* No call chains after `either()`. */
    private RecordQueryBuilder<I, EntityRecord>
    copyDescendants(RecordQueryBuilder<I, EntityRecord> builder,
                    QueryPredicate<S> sourcePredicate) {
        var params = sourcePredicate.allParams();
        var children = sourcePredicate.children();
        var operator = sourcePredicate.operator();
        if (operator == LogicalOperator.AND) {
            copyParameters(this, params);
            for (var child : children) {
                copyDescendants(builder, child);
            }
        } else {
            var eitherStatements =
                    toEither(params, children);
            builder.either(eitherStatements);
        }
        return builder;
    }

    /**
     * Creates {@link Either} statements for each of the passed parameters and predicates
     * and returns them all as a new {@code ImmutableList}.
     */
    @SuppressWarnings({"Immutable"      /* Using `builder` in lambda is fine. */,
            "MethodWithMultipleLoops"   /* Transforming params and predicates
                                            are very related to each other. */
    })
    private ImmutableList<Either<RecordQueryBuilder<I, EntityRecord>>>
    toEither(Iterable<SubjectParameter<?, ?, ?>> params, Iterable<QueryPredicate<S>> predicates) {
        ImmutableList.Builder<Either<RecordQueryBuilder<I, EntityRecord>>> result =
                ImmutableList.builder();
        for (var param : params) {
            result.add(builder -> addParameter(builder,
                                               param.column(), param.operator(), param.value()));
        }
        for (var predicate : predicates) {
            result.add(builder -> copyDescendants(builder, predicate));
        }
        return result.build();
    }

    /**
     * Copies the passed parameters to the specified {@code builder}.
     */
    @CanIgnoreReturnValue
    private RecordQueryBuilder<I, EntityRecord>
    copyParameters(RecordQueryBuilder<I, EntityRecord> builder,
                   Iterable<SubjectParameter<?, ?, ?>> params) {
        for (var parameter : params) {
            addParameter(builder, parameter.column(), parameter.operator(), parameter.value());
        }
        return builder;
    }

    /**
     * Adds a single parameter to the specified {@code builder}.
     *
     * @param builder
     *         the builder to add the parameter to
     * @param source
     *         source column for the parameter
     * @param operator
     *         an operator comparing the column values
     * @param value
     *         the value to match the actual column values to
     * @return the same {@code builder} as passed, for call chaining
     */
    @CanIgnoreReturnValue
    private <V> RecordQueryBuilder<I, EntityRecord>
    addParameter(RecordQueryBuilder<I, EntityRecord> builder, Column<?, ?> source,
                 ComparisonOperator operator, V value) {
        @SuppressWarnings("unchecked")
        var castSource = (Column<?, V>) source;
        var column = AsEntityRecordColumn.apply(castSource);
        var where = builder.where(column);
        switch (operator) {
            case EQUALS:
                return where.is(value);
            case GREATER_OR_EQUALS:
                return where.isGreaterOrEqualTo(value);
            case GREATER_THAN:
                return where.isGreaterThan(value);
            case LESS_OR_EQUALS:
                return where.isLessOrEqualTo(value);
            case LESS_THAN:
                return where.isLessThan(value);
            default:
                throw newIllegalStateException("Unknown comparison operator `%s`.",
                                               operator);
        }
    }
}
