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
                .assumingTests(true)
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
