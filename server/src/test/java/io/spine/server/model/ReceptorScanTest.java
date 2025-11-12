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

package io.spine.server.model;

import io.spine.server.event.model.SubscriberSignature;
import io.spine.server.model.given.map.ARejectionSubscriber;
import io.spine.server.model.given.map.FilteredSubscription;
import io.spine.server.model.given.map.Int32ImportedTypedSubscriber;
import io.spine.string.StringifierRegistry;
import io.spine.string.Stringifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.model.ReceptorScan.findMethodsBy;

@DisplayName("`MethodScan` should")
class ReceptorScanTest {

    /**
     * Registers the stringifier for {@code Integer}, which is used for parsing filter field values.
     */
    @BeforeAll
    static void prepare() {
        StringifierRegistry.instance()
                           .register(Stringifiers.forInteger(), Integer.TYPE);
    }

    @Test
    @DisplayName("provide a map with filter-less keys")
    void noFiltersInKeys() {
        var map = findMethodsBy(FilteredSubscription.class, new SubscriberSignature());
        assertThat(map)
                .hasSize(2);
        for (var key : map.keySet()) {
            assertThat(key)
                    .isEqualTo(key.withoutFilter());
        }
    }

    @Test
    @DisplayName("allow multiple subscriptions to the same rejection with different causes")
    void multipleRejectionSubscriptions() {
        var map = findMethodsBy(ARejectionSubscriber.class, new SubscriberSignature());
        assertThat(map.keys())
                .hasSize(2);
    }

    @Test
    @DisplayName("not include bridge methods")
    void bridgeMethods() {
        var map =
                findMethodsBy(Int32ImportedTypedSubscriber.class, new SubscriberSignature());
        assertThat(map.keys())
                .hasSize(1);
    }
}
