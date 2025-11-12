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
import com.google.protobuf.FieldMask;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.ResponseFormats.responseFormat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ResponseFormats` should")
class ResponseFormatsTest extends UtilityClassTest<ResponseFormats> {

    ResponseFormatsTest() {
        super(ResponseFormats.class);
    }

    @Override
    protected void configure(NullPointerTester tester) {
        super.configure(tester);
        tester.setDefault(FieldMask.class, FieldMask.getDefaultInstance())
              .setDefault(OrderBy.class, OrderBy.getDefaultInstance());
    }

    @Test
    @DisplayName("create `ResponseFormat` with `OrderBy`")
    void createWithOrder() {
        var orderBy = orderBy();
        var format = responseFormat(null, orderBy, null);

        assertThat(format.getFieldMask()).isEqualTo(FieldMask.getDefaultInstance());
        assertThat(format.getOrderByList()).containsExactly(orderBy);
        assertThat(format.getLimit()).isEqualTo(0);
    }

    @Test
    @DisplayName("create `ResponseFormat` with `FieldMask`")
    void createWithFieldMast() {
        var fieldMask = fieldMask();
        var format = responseFormat(fieldMask, null, null);

        assertThat(format.getFieldMask()).isEqualTo(fieldMask);
        assertThat(format.getOrderByList()).isEmpty();
        assertThat(format.getLimit()).isEqualTo(0);
    }

    @Test
    @DisplayName("accept only positive limit values")
    @SuppressWarnings({"ResultOfMethodCallIgnored", "MethodWithMultipleLoops"})
    void acceptOnlyPositiveLimitValues() {
        var inacceptableValues = new int[]{-42, -1, 0};
        var acceptableValues = new int[]{2020, 17, 1};
        for (var value : inacceptableValues) {
            assertThrows(IllegalArgumentException.class,
                         () -> responseFormat(null, null, value));
        }
        for (var value : acceptableValues) {
            var format = responseFormat(null, null, value);
            assertThat(format.getLimit()).isEqualTo(value);
        }
    }

    private static FieldMask fieldMask() {
        return FieldMask.newBuilder()
                        .addPaths("some_description")
                        .build();
    }

    private static OrderBy orderBy() {
        return OrderBy.newBuilder()
                      .setDirection(OrderBy.Direction.DESCENDING)
                      .setColumn("number_of_issues")
                      .build();
    }
}
