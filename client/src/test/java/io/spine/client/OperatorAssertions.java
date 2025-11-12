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

import io.spine.client.Filter.Operator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.GREATER_THAN;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_THAN;
import static io.spine.client.OperatorEvaluator.eval;

/**
 * Provides custom assertions for tests related
 * to {@link OperatorEvaluator#eval(Object, Operator, Object)}.
 */
final class OperatorAssertions {

    /** Prevents instantiation of this utility class. */
    private OperatorAssertions() {
    }

    static <T> void
    assertTrue(@Nullable T left, Operator operator, @Nullable T right, String message) {
        Assertions.assertTrue(eval(left, operator, right), message);
    }

    static <T>
    void assertTrue(@Nullable T left, Operator operator, @Nullable T right) {
        Assertions.assertTrue(eval(left, operator, right));
    }

    static <T> void
    assertFalse(@Nullable T left, Operator operator, @Nullable T right, String message) {
        Assertions.assertFalse(eval(left, operator, right), message);
    }

    static <T>
    void assertFalse(@Nullable T left, Operator operator, @Nullable T right) {
        Assertions.assertFalse(eval(left, operator, right));
    }

    static void assertGreater(Object left, Object right) {
        assertStrict(left, right, GREATER_THAN);
    }

    static void assertLess(Object left, Object right) {
        assertStrict(left, right, LESS_THAN);
    }

    static void assertGreaterOrEqual(Object obj, Object less) {
        assertNotStrict(obj, less, GREATER_OR_EQUAL, GREATER_THAN);
    }

    static void assertLessOrEqual(Object obj, Object less) {
        assertNotStrict(obj, less, LESS_OR_EQUAL, LESS_THAN);
    }

    private static void
    assertStrict(Object left, Object right, Operator operator) {
        assertTrue(left, operator, right);
        assertFalse(right, operator, left);
        assertFalse(left, EQUAL, right);
    }

    private static void
    assertNotStrict(Object obj, Object other, Operator operator, Operator strictOperator) {
        assertStrict(obj, other, strictOperator);

        assertTrue(obj, operator, obj);
        assertTrue(obj, EQUAL, obj);
    }
}
