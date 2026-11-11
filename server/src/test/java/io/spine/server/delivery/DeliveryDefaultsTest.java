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

package io.spine.server.delivery;

import io.spine.base.Identifier;
import io.spine.server.NodeId;
import io.spine.server.storage.Storage;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests on the default values set in the created {@code Delivery} instance.
 */
@DisplayName("`Delivery` configured by default should")
class DeliveryDefaultsTest {

    @Test
    @DisplayName("initialize the `InboxStorage` in a single-tenant mode")
    void createSingleTenantInboxStorage() {
        var storage = defaultDelivery().inboxStorage();
        assertSingleTenant(storage);
    }

    @Test
    @DisplayName("initialize the `CatchUp` in a single-tenant mode")
    void createSingleTenantCatchUpStorage() {
        var storage = defaultDelivery().catchUpStorage();
        assertSingleTenant(storage);
    }

    private static Delivery defaultDelivery() {
        var nodeId = NodeId.newBuilder()
                .setValue(Identifier.newUuid())
                .build();
        return Delivery.newBuilder(nodeId, InMemoryStorageFactory.newInstance())
                .build();
    }

    private static void assertSingleTenant(Storage<?, ?> storage) {
        assertThat(storage).isNotNull();
        assertThat(storage.isMultiTenant()).isFalse();
    }
}
