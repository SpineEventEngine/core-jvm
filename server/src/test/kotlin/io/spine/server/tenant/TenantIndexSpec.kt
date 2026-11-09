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
import io.kotest.matchers.collections.shouldContainExactly
import io.spine.server.tenant.given.InMemoryTenantIndex
import io.spine.testing.core.given.GivenTenantId.generate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`TenantIndex` should")
internal class TenantIndexSpec {

    @Test
    fun `tell that it has a tenant it keeps`() {
        val tenant = generate()
        val index = InMemoryTenantIndex()
        index.keep(tenant)

        index.contains(tenant).shouldBeTrue()
    }

    @Test
    fun `tell that it does not have a tenant, without keeping it`() {
        val kept = generate()
        val index = InMemoryTenantIndex()
        index.keep(kept)

        index.contains(generate()).shouldBeFalse()
        index.all() shouldContainExactly setOf(kept)
    }
}
