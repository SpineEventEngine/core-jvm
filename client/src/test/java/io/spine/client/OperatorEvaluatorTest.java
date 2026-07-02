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

import com.google.common.testing.NullPointerTester;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static com.google.protobuf.util.Timestamps.add;
import static io.spine.base.Time.currentTime;
import static io.spine.client.Filter.Operator;
import static io.spine.client.Filter.Operator.CFO_UNDEFINED;
import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.GREATER_THAN;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_THAN;
import static io.spine.client.OperatorAssertions.assertFalse;
import static io.spine.client.OperatorAssertions.assertGreater;
import static io.spine.client.OperatorAssertions.assertGreaterOrEqual;
import static io.spine.client.OperatorAssertions.assertLess;
import static io.spine.client.OperatorAssertions.assertLessOrEqual;
import static io.spine.client.OperatorAssertions.assertTrue;
import static io.spine.client.OperatorEvaluator.eval;
import static io.spine.protobuf.Durations2.seconds;
import static io.spine.testing.Assertions.assertIllegalArgument;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({
        "DuplicateStringLiteralInspection" /* Common test display names */,
        "ThrowableNotThrown" /* from custom assertions */
})
@DisplayName("`OperatorEvaluator` should")
class OperatorEvaluatorTest {

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .testStaticMethods(OperatorEvaluator.class, PACKAGE);
    }

    @SuppressWarnings({"RedundantStringConstructorCall", "RedundantSuppression"})
        // We need an equal but not the same object
    @Test
    @DisplayName("compare equal instances")
    void compareEqual() {
        var left = "myobject";
        Object right = new String(left);
        Object third = new String(left);

        // The checks taken from the java.lang.Object.equals Javadoc
        assertTrue(left, EQUAL, right, "basic");
        assertTrue(right, EQUAL, left, "symmetric");
        assertTrue(left, EQUAL, left, "reflective");
        Assertions.assertTrue(eval(left, EQUAL, third) || eval(third, EQUAL, right), "transitive");
        assertTrue(null, EQUAL, null, "nullable");
        assertTrue(left, EQUAL, right, "consistent");
    }

    @Test
    @DisplayName("compare non-equal instances")
    void compareNonEqual() {
        Object left = "one!";
        Object right = "another!";

        assertFalse(left, EQUAL, right, "direct order check");
        assertFalse(right, EQUAL, left, "reverse order check");
    }

    @Nested
    @DisplayName("compare timestamps by")
    class CompareTimestampsBy {

        @Test
        @DisplayName("`GREATER_THAN`")
        void gt() {
            var delta = seconds(5);
            var small = currentTime();
            var medium = add(small, delta);
            var big = add(medium, delta);

            assertTrue(medium, GREATER_THAN, small);
            assertTrue(big, GREATER_THAN, medium);
            assertTrue(big, GREATER_THAN, small);

            assertFalse(small, GREATER_THAN, small);
            assertFalse(small, GREATER_THAN, big);
            assertFalse(nullRef(), GREATER_THAN, small);
        }

        @Test
        @DisplayName("`GREATER_OR_EQUAL`")
        void ge() {
            var delta = seconds(5);
            var small = currentTime();
            var medium = add(small, delta);
            var big = add(medium, delta);

            assertTrue(medium, GREATER_OR_EQUAL, small);
            assertTrue(big, GREATER_OR_EQUAL, medium);
            assertTrue(big, GREATER_OR_EQUAL, small);
            assertTrue(small, GREATER_OR_EQUAL, small);

            assertFalse(small, GREATER_OR_EQUAL, big);
            assertFalse(nullRef(), GREATER_OR_EQUAL, small);
        }

        @Test
        @DisplayName("`LESS_THAN`")
        void lt() {
            var delta = seconds(5);
            var small = currentTime();
            var medium = add(small, delta);
            var big = add(medium, delta);

            assertTrue(medium, LESS_THAN, big);
            assertTrue(small, LESS_THAN, medium);
            assertTrue(small, LESS_THAN, big);

            assertFalse(big, LESS_THAN, big);
            assertFalse(big, LESS_THAN, small);
            assertFalse(nullRef(), LESS_THAN, nullRef());
        }

        @Test
        @DisplayName("`LESS_OR_EQUAL`")
        void le() {
            var delta = seconds(5);
            var small = currentTime();
            var medium = add(small, delta);
            var big = add(medium, delta);

            assertTrue(medium, LESS_OR_EQUAL, big);
            assertTrue(small, LESS_OR_EQUAL, medium);
            assertTrue(small, LESS_OR_EQUAL, big);

            assertTrue(medium, LESS_OR_EQUAL, medium);
            assertFalse(big, LESS_OR_EQUAL, small);
            assertFalse(medium, LESS_OR_EQUAL, nullRef());
        }
    }

    @Nested
    @DisplayName("compare ints by")
    class CompareIntsBy {

        @Test
        @DisplayName("`GREATER_THAN`")
        void gt() {
            assertGreater(42, 31);
        }

        @Test
        @DisplayName("`GREATER_OR_EQUAL`")
        void ge() {
            assertGreaterOrEqual(42, 41);
        }

        @Test
        @DisplayName("`LESS_THAN`")
        void lt() {
            assertLess(42, 314);
        }

        @Test
        @DisplayName("`LESS_OR_EQUAL`")
        void le() {
            assertLessOrEqual(42, 43);
        }
    }

    @Nested
    @DisplayName("compare strings by")
    class CompareStringsBy {

        @Test
        @DisplayName("`GREATER_THAN`")
        void gt() {
            assertGreater("a", "!");
        }

        @Test
        @DisplayName("`GREATER_OR_EQUAL`")
        void ge() {
            assertGreaterOrEqual("d", "c");
        }

        @Test
        @DisplayName("`LESS_THAN`")
        void lt() {
            assertLess("Z", "a");
        }

        @Test
        @DisplayName("`LESS_OR_EQUAL`")
        void le() {
            assertLessOrEqual("a", "b");
        }
    }

    @Nested
    @DisplayName("compare doubles by")
    class CompareDoublesBy {

        @Test
        @DisplayName("`GREATER_THAN`")
        void gt() {
            assertGreater(42, 31);
        }

        @Test
        @DisplayName("`GREATER_OR_EQUAL`")
        void ge() {
            assertGreaterOrEqual(42.1, 42.01);
        }

        @Test
        @DisplayName("`LESS_THAN`")
        void lt() {
            assertLess(42.81, 314.0);
        }

        @Test
        @DisplayName("`LESS_OR_EQUAL`")
        void le() {
            assertLessOrEqual(42.999, 43.0);
        }
    }

    @Nested
    @DisplayName("fail to compare unsupported types by")
    class NotCompareUnsupportedBy {

        @Test
        @DisplayName("`GREATER_THAN`")
        void gt() {
            checkFailsToCompareBy(GREATER_THAN);
        }

        @Test
        @DisplayName("`GREATER_OR_EQUAL`")
        void ge() {
            checkFailsToCompareBy(GREATER_OR_EQUAL);
        }

        @Test
        @DisplayName("`LESS_THAN`")
        void lt() {
            checkFailsToCompareBy(LESS_THAN);
        }

        @Test
        @DisplayName("`LESS_OR_EQUAL`")
        void le() {
            checkFailsToCompareBy(LESS_OR_EQUAL);
        }

        private void checkFailsToCompareBy(Operator operator) {
            assertThrows(UnsupportedOperationException.class,
                         () -> eval(FaultyComparisonType.INSTANCE,
                                    operator,
                                    FaultyComparisonType.INSTANCE));
        }
    }

    @Test
    @DisplayName("fail to compare different types")
    void notCompareDifferentTypes() {
        assertIllegalArgument(() -> eval("7", GREATER_THAN, 6));
    }

    @Test
    @DisplayName("fail to compare by invalid operator")
    void notCompareByInvalidOperator() {
        assertIllegalArgument(() -> eval("a", CFO_UNDEFINED, "b"));
    }

    private static class FaultyComparisonType {

        @SuppressWarnings("InstantiationOfUtilityClass") // Not a utility class
        public static final FaultyComparisonType INSTANCE = new FaultyComparisonType();

        private FaultyComparisonType() {
            // Singleton type.
        }
    }
}
