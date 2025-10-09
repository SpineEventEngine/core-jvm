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

package io.spine.server.storage.memory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.annotation.VisibleForTesting;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.query.SubjectParameter;
import io.spine.server.storage.RecordWithColumns;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Matches the records to the {@linkplain RecordQuery#subject() subject} of a {@link RecordQuery}.
 *
 * @param <I>
 *         the type of the identifiers of the records
 * @param <R>
 *         the type of the messages stored as records
 */
public class RecordQueryMatcher<I, R extends Message>
        implements Predicate<@Nullable RecordWithColumns<I, R>> {

    private final ImmutableSet<I> acceptedIds;
    private final QueryPredicate<R> predicate;

    /**
     * Creates a new matcher for the given subject.
     */
    RecordQueryMatcher(Subject<I, R> subject) {
        checkNotNull(subject);
        // Pack IDs from the query for faster search using packed IDs from loaded records.
        this.acceptedIds = subject.id()
                                  .values();
        this.predicate = subject.predicate();
    }

    @VisibleForTesting
    RecordQueryMatcher(RecordQuery<I, R> query) {
        this(checkNotNull(query).subject());
    }

    @Override
    public boolean test(@Nullable RecordWithColumns<I, R> input) {
        if (input == null) {
            return false;
        }
        var result = idMatches(input) && columnValuesMatch(input);
        return result;
    }

    private boolean idMatches(RecordWithColumns<I, R> record) {
        if (acceptedIds.isEmpty()) {
            return true;
        }
        var actualId = record.id();
        return acceptedIds.contains(actualId);
    }

    private boolean columnValuesMatch(RecordWithColumns<I, R> record) {
        return checkPredicate(record, predicate);
    }

    private static <I, R extends Message> boolean
    checkPredicate(RecordWithColumns<I, R> record, QueryPredicate<R> predicate) {
        boolean match;

        var operator = predicate.operator();
        var parameters = predicate.parameters();
        var children = predicate.children();
        switch (operator) {
            case AND:
                match = checkAnd(record, parameters, children);
                break;
            case OR:
                match = checkEither(record, parameters, children);
                break;
            default:
                throw newIllegalArgumentException("Logical operator `%s` is invalid.",
                                                  operator);
        }
        return match;
    }

    private static <I, R extends Message> boolean
    checkAnd(RecordWithColumns<I, R> record, ImmutableList<SubjectParameter<R, ?, ?>> params,
             ImmutableList<QueryPredicate<R>> predicates) {
        if (params.isEmpty() && predicates.isEmpty()) {
            return true;
        }
        var paramsMatch =
                params.stream()
                      .allMatch(param -> matches(record, param));
        if (paramsMatch) {
            var predicatesMatch =
                    predicates.stream()
                              .allMatch(predicate -> checkPredicate(record,
                                                                    predicate));
            return predicatesMatch;
        }
        return false;
    }

    private static <I, R extends Message> boolean
    checkEither(RecordWithColumns<I, R> record,
                ImmutableList<SubjectParameter<R, ?, ?>> params,
                ImmutableList<QueryPredicate<R>> predicates) {
        if (params.isEmpty() && predicates.isEmpty()) {
            return true;
        }
        var paramsMatch =
                params.stream()
                      .anyMatch(param -> matches(record, param));
        if (!paramsMatch) {
            var predicatesMatch =
                    predicates.stream()
                              .anyMatch(predicate -> checkPredicate(record, predicate));
            return predicatesMatch;
        }
        return true;
    }

    private static <I, R extends Message> boolean
    matches(RecordWithColumns<I, R> recWithColumns, SubjectParameter<R, ?, ?> param) {
        var column = param.column();
        if (!recWithColumns.hasColumn(column.name())) {
            return false;
        }
        @Nullable Object columnValue = recWithColumns.columnValue(param.column()
                                                                       .name());
        var result = checkSingleParameter(param, columnValue);
        return result;
    }

    private static <R extends Message> boolean
    checkSingleParameter(SubjectParameter<R, ?, ?> parameter, @Nullable Object actualValue) {
        if (actualValue == null) {
            return false;
        }
        var paramValue = parameter.value();
        var result = parameter.operator()
                              .eval(actualValue, paramValue);
        return result;
    }
}
