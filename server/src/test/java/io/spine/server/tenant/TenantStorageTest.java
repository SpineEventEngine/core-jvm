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

package io.spine.server.tenant;

import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`TenantStorage` should")
class TenantStorageTest {

    private TenantStorage<?> storage;

    @BeforeEach
    void setUp() {
        var storageFactory = ServerEnvironment.instance().storageFactory();
        storage = new DefaultTenantStorage(storageFactory);
        BoundedContextBuilder
                .assumingTests(/* multitenant = */ true)
                .setTenantIndex(storage)
                .build();
    }

    @Test
    @DisplayName("cache passed value")
    void cachePassedValue() {
        var tenantId = generate();
        storage.keep(tenantId);

        Optional<?> optional = storage.read(tenantId);
        assertThat(optional).isPresent();
        assertTrue(storage.cached(tenantId));
    }

    @Test
    @DisplayName("evict from cache")
    void evictFromCache() {
        var tenantId = generate();

        storage.keep(tenantId);
        assertTrue(storage.unCache(tenantId));
        assertFalse(storage.unCache(tenantId));
    }

    @Test
    @DisplayName("clear cache")
    void clearCache() {
        var tenantId = generate();

        storage.keep(tenantId);

        storage.clearCache();

        assertFalse(storage.unCache(tenantId));
    }
}
