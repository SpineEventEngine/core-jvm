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

package io.spine.server.model;

import com.google.common.collect.ImmutableList;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * The mismatch of a signature.
 */
@Internal
public final class SignatureMismatch {

    /**
     * The criterion, which requirements were not met.
     */
    private final MatchCriterion unmetCriterion;

    /**
     * The severity of the mismatch.
     *
     * <p>Users of this class should consider building their handling logic depending
     * on the value of this field. E.g. {@link Severity#ERROR ERROR} severity should probably
     * be handled by raising an exception, such as {@link SignatureMismatchException}.
     */
    private final Severity severity;

    /**
     * The message telling what the mismatch is.
     *
     * <p>This field exists to avoid formatting the message each time from the message template.
     */
    private final String message;

    private SignatureMismatch(MatchCriterion criterion, Object[] values) {
        this.unmetCriterion = criterion;
        this.severity = criterion.severity();
        this.message = criterion.formatMsg(values);
    }

    static String formatMsg(Method method, Iterable<SignatureMismatch> mismatches) {
        var list = ImmutableList.copyOf(mismatches);
        var singularOrPlural = list.size() > 1 ? "issues" : "an issue";
        var methodRef = Methods.reference(method);
        var prolog = format(
                "The method `%s` is declared with %s:%n",
                methodRef,
                singularOrPlural
        );
        var issues = buildList(list);
        return prolog + issues;
    }

    private static @NonNull StringBuilder buildList(ImmutableList<SignatureMismatch> list) {
        var stringBuilder = new StringBuilder(100);
        var lastMismatch = list.get(list.size() - 1);
        var newLine = System.lineSeparator();
        list.forEach(mismatch -> {
            var kind = mismatch.isError() ? "Error: " : "Warning: ";
            stringBuilder.append(" - ")
                         .append(kind)
                         .append(mismatch);
            var isLast = mismatch.equals(lastMismatch);
            if (!isLast) {
                stringBuilder.append(newLine);
            }
        });
        return stringBuilder;
    }

    /** Returns whether this mismatch is of {@code ERROR} severity. */
    boolean isError() {
        return severity == Severity.ERROR;
    }

    /** Returns whether this mismatch is of {@code WARN} severity. */
    boolean isWarning() {
        return severity == Severity.WARN;
    }

    /**
     * Returns the match criterion, which requirements were violated.
     */
    @VisibleForTesting
    public MatchCriterion getUnmetCriterion() {
        return unmetCriterion;
    }

    /**
     * Creates a new mismatch from the criterion and the values, which violated the criterion.
     *
     * @param criterion
     *         the criterion
     * @param values
     *         the values, which did not meet the criterion requirements
     * @return a new {@code SignatureMismatch} instance wrapped in {@code Optional}
     *         which is guaranteed to be non-empty
     */
    static Optional<SignatureMismatch> create(MatchCriterion criterion, Object... values) {
        checkNotNull(criterion);
        checkNotNull(values);
        var result = new SignatureMismatch(criterion, values);
        return Optional.of(result);
    }

    /**
     * The severity level of the mismatch.
     */
    enum Severity {
        /**
         * The mismatch of {@code ERROR} level means that the violation is a show-stopper.
         */
        ERROR,

        /**
         * The mismatch of {@code WARN} level means that the recommended criterion was not met,
         * however, the application execution may proceed.
         */
        WARN
    }

    @Override
    public String toString() {
        return message;
    }
}
