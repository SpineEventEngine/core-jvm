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

package io.spine.server.tenant

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.spine.core.TenantId
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.testing.core.given.GivenTenantId.generate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Checks how [TenantStorage] tells whether it has a tenant.
 *
 * Supplements `TenantStorageTest`, which checks the cache of the storage.
 */
@DisplayName("`TenantStorage` should")
internal class TenantStorageSpec {

    private lateinit var storage: DefaultTenantStorage

    @BeforeEach
    fun createStorage() {
        storage = DefaultTenantStorage(InMemoryStorageFactory.newInstance())
    }

    @AfterEach
    fun closeStorage() {
        storage.close()
    }

    @Nested inner class
    `tell that it has a tenant` {

        @Test
        fun `which it keeps`() {
            val tenant = generate()
            storage.keep(tenant)

            storage.contains(tenant).shouldBeTrue()
        }

        @Test
        fun `whose record is stored, but not cached, and cache it`() {
            val tenant = generate()
            storeRecordOf(tenant)
            storage.cached(tenant).shouldBeFalse()

            storage.contains(tenant).shouldBeTrue()
            storage.cached(tenant).shouldBeTrue()
        }
    }

    @Nested inner class
    `tell that it does not have a tenant` {

        @Test
        fun `without storing it`() {
            storage.contains(generate()).shouldBeFalse()

            storage.all().shouldBeEmpty()
        }

        @Test
        fun `without caching the answer`() {
            val tenant = generate()
            storage.contains(tenant).shouldBeFalse()
            storage.cached(tenant).shouldBeFalse()

            storeRecordOf(tenant)

            storage.contains(tenant).shouldBeTrue()
        }
    }

    /**
     * Stores the record of the tenant the way another server node would,
     * bypassing the cache of the storage under the test.
     */
    private fun storeRecordOf(tenant: TenantId) {
        storage.write(tenant, storage.create(tenant))
    }
}
