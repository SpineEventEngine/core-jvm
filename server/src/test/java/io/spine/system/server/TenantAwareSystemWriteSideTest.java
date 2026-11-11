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

package io.spine.system.server;

import io.spine.base.EventMessage;
import io.spine.core.TenantId;
import io.spine.testdata.Sample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.system.server.WriteSideFunction.delegatingTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`TenantAwareSystemWriteSide` should")
class TenantAwareSystemWriteSideTest {

    private static final String POST_EVENTS = "post system events";

    private MemoizingWriteSide delegate;

    @Nested
    @DisplayName("in single-tenant env")
    class SingleTenant {

        private final TenantId defaultTenant = TenantId.getDefaultInstance();

        @BeforeEach
        void setUp() {
            delegate = MemoizingWriteSide.singleTenant();
        }

        @Test
        @DisplayName(POST_EVENTS)
        void postEvents() {
            postEventAndCheck(defaultTenant);
        }
    }

    @Nested
    @DisplayName("in multi-tenant env")
    class MultiTenant {

        private TenantId tenantId;

        @BeforeEach
        void setUp() {
            delegate = MemoizingWriteSide.multiTenant();
            tenantId = TenantId.newBuilder()
                    .setValue(TenantAwareSystemWriteSideTest.class.getName())
                    .build();
        }

        @Test
        @DisplayName(POST_EVENTS)
        void postEvents() {
            postEventAndCheck(tenantId);
            assertEquals(tenantId, delegate.lastSeenEvent().tenant());
        }
    }

    private void postEventAndCheck(TenantId tenantId) {
        EventMessage event = Sample.messageOfType(PersonNameCreated.class);
        var writeSide = delegatingTo(delegate).get(tenantId);
        writeSide.postEvent(event);

        assertEquals(event, delegate.lastSeenEvent().message());
    }
}
