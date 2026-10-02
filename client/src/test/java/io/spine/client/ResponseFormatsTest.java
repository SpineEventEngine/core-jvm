/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.client;

import com.google.common.testing.NullPointerTester;
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
        tester.setDefault(OrderBy.class, OrderBy.getDefaultInstance());
    }

    @Test
    @DisplayName("create `ResponseFormat` with `OrderBy`")
    void createWithOrder() {
        var orderBy = orderBy();
        var format = responseFormat(orderBy, null);

        assertThat(format.getOrderByList()).containsExactly(orderBy);
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
                         () -> responseFormat(null, value));
        }
        for (var value : acceptableValues) {
            var format = responseFormat(null, value);
            assertThat(format.getLimit()).isEqualTo(value);
        }
    }

    private static OrderBy orderBy() {
        return OrderBy.newBuilder()
                      .setDirection(OrderBy.Direction.DESCENDING)
                      .setColumn("number_of_issues")
                      .build();
    }
}
