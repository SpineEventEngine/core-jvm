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

package io.spine.server.tuple;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;
import io.spine.server.tuple.Element.AValue;
import io.spine.server.tuple.Element.BValue;
import io.spine.testing.TestValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`Tuple` should")
@SuppressWarnings({
        "FieldNamingConvention",
        "InstanceVariableNamingConvention" /* OK for tuple entry values. */
})
class TupleTest {

    private final StringValue a = TestValues.newUuidValue();
    private final EitherOf2<Timestamp, BoolValue> b = EitherOf2.withA(Time.currentTime());

    private TTuple<StringValue, EitherOf2<Timestamp, BoolValue>> tuple;

    @BeforeEach
    void setUp() {
        tuple = new TTuple<>(a, b);
    }

    @Test
    @DisplayName("prohibit `Empty` values")
    void prohibitEmptyValues() {
        assertThrows(IllegalArgumentException.class,
                     () -> new TTuple<>(TestValues.newUuidValue(), Empty.getDefaultInstance()));
    }

    @Test
    @DisplayName("allow `Either` argument")
    void allowEitherArgument() {
        assertEquals(a, tuple.getA());
        assertEquals(b, tuple.getB());
    }

    @Test
    @DisplayName("return value from `Either` on iteration")
    void returnValueOnIteration() {
        var iterator = tuple.iterator();

        assertEquals(a, iterator.next());
        assertEquals(b.getA(), iterator.next());
        assertFalse(iterator.hasNext());
    }

    /**
     * Descendant to test abstract base.
     */
    @SuppressWarnings("unchecked")
    private static class TTuple<A extends Message, B>
            extends Tuple
            implements AValue<A>, BValue<B> {

        private static final long serialVersionUID = 0L;

        TTuple(A a, B b) {
            super(a, b);
        }

        @Override
        public A getA() {
            return (A) get(0);
        }

        @Override
        public boolean hasA() {
            return isOptionalPresent(getA());
        }

        @Override
        public B getB() {
            return (B) get(1);
        }

        @Override
        public boolean hasB() {
            return isOptionalPresent(getB());
        }
    }
}
