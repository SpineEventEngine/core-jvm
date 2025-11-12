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

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.client.Filter.Operator;
import static io.spine.time.TimestampExtensions.isAfter;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static java.lang.String.format;

/**
 * A boolean non-typed comparison operation on two given instances.
 *
 * <p><a name="supported_types"><strong>Supported types</strong></a>
 *
 * <p>The equality checks support all types. The check is performed via the {@link Object#equals}
 * method. A {@code null} reference is equal to another {@code null} reference.
 *
 * <p>Order-based comparison supports only {@code Comparable} types and
 * {@link com.google.protobuf.Timestamp}. When trying to compare unsupported types,
 * an {@code UnsupportedOperationException} is thrown.
 *
 * <p>It is required that the runtime Java class of the two compared values is the same. Otherwise,
 * an {@code IllegalArgumentException} is thrown.
 *
 * @see io.spine.client.CompositeFilter.CompositeOperator for the comparison strategies
 */
@Internal
public enum OperatorEvaluator {

    EQUAL {
        @Override
        public boolean eval(@Nullable Object left, @Nullable Object right) {
            return Objects.equals(left, right);
        }
    },
    GREATER_THAN {
        @SuppressWarnings("ChainOfInstanceofChecks") // Generic but limited operand types
        @Override
        public boolean eval(@Nullable Object left, @Nullable Object right) {
            if (left == null || right == null) {
                return false;
            }
            if (left.getClass() != right.getClass()) {
                throw newIllegalArgumentException(
                        "Cannot compare an instance of `%s` to an instance of `%s`.",
                        classNameOf(left), classNameOf(right)
                );
            }
            if (left instanceof Timestamp) {
                return isAfter((Timestamp) left, (Timestamp) right);
            }
            if (left instanceof Comparable<?>) {
                return compare((Comparable<?>) left, (Comparable<?>) right);
            }
            throw new UnsupportedOperationException(format(
                    "Comparison operations are not supported for type `%s`.",
                    classNameOf(left))
            );
        }

        private String classNameOf(Object left) {
            return left.getClass()
                       .getCanonicalName();
        }

        @SuppressWarnings({"rawtypes", "unchecked"}) // Type is unknown but checked at runtime.
        private boolean compare(Comparable<?> left, Comparable<?> right) {
            Comparable cmpLeft = left;
            Comparable cmpRight = right;
            var result = cmpLeft.compareTo(cmpRight);
            return result > 0;
        }
    },
    LESS_THAN {
        @Override
        public boolean eval(@Nullable Object left, @Nullable Object right) {
            return GREATER_THAN.eval(right, left);
        }
    },
    GREATER_OR_EQUAL {
        @Override
        public boolean eval(@Nullable Object left, @Nullable Object right) {
            return GREATER_THAN.eval(left, right)
                    || EQUAL.eval(left, right);
        }
    },
    LESS_OR_EQUAL {
        @Override
        public boolean eval(@Nullable Object left, @Nullable Object right) {
            return LESS_THAN.eval(left, right)
                || EQUAL.eval(left, right);
        }
    };

    private static final ImmutableMap<Operator, OperatorEvaluator> EVALUATORS =
            ImmutableMap.<Operator, OperatorEvaluator>builder()
                    .put(Operator.EQUAL, EQUAL)
                    .put(Operator.GREATER_THAN, GREATER_THAN)
                    .put(Operator.LESS_THAN, LESS_THAN)
                    .put(Operator.GREATER_OR_EQUAL, GREATER_OR_EQUAL)
                    .put(Operator.LESS_OR_EQUAL, LESS_OR_EQUAL)
                    .build();

    /**
     * Evaluates the given expression.
     *
     * <p>For example, if operands where {@code 42} and {@code 9} (exactly in that order) and
     * the operator was {@link Operator#GREATER_THAN GREATER_THAN}, then this function could be
     * expressed as {@code 42 > 9}. The function returns the {@code boolean} result of
     * the evaluation.
     *
     * @param left
     *         the left operand
     * @param operator
     *         the comparison operator
     * @param right
     *         the right operand
     * @param <T>
     *         the type of the compared values
     * @return {@code true} if the operands match the operator, {@code false} otherwise
     * @throws UnsupportedOperationException
     *         if the operation is <a href="#supported_types">not supported</a> for the given
     *         data types
     */
    public static <T> boolean eval(@Nullable T left, Operator operator, @Nullable T right)
            throws UnsupportedOperationException {
        checkNotNull(operator);
        var evaluator = EVALUATORS.get(operator);
        checkArgument(evaluator != null, operator);
        var result = evaluator.eval(left, right);
        return result;
    }

    /**
     * Evaluates the expression of joining the given operands with a certain operator.
     *
     * @see OperatorEvaluator#eval for the detailed behaiour
     * description
     * @return {@code true} if the expression evaluates into {@code true}, {@code false} otherwise
     */
    abstract boolean eval(@Nullable Object left, @Nullable Object right);
}
