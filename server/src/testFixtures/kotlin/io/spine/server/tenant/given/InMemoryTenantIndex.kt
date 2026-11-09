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

package io.spine.server.tenant.given

import io.spine.core.TenantId
import io.spine.server.tenant.TenantIndex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [TenantIndex] that keeps the tenants in memory, and counts how many times
 * it is closed.
 *
 * Uses the default implementation of [contains][TenantIndex.contains], so that tests
 * can check it.
 *
 * @param failOnClose Tells if [close] throws [IllegalStateException] after counting the call.
 */
public class InMemoryTenantIndex(
    private val failOnClose: Boolean = false
) : TenantIndex {

    private val tenants = ConcurrentHashMap.newKeySet<TenantId>()

    private val closings = AtomicInteger()

    /**
     * How many times the index was closed.
     */
    public val closeCount: Int
        get() = closings.get()

    override fun keep(id: TenantId) {
        tenants.add(id)
    }

    override fun all(): Set<TenantId> = tenants.toSet()

    override fun isOpen(): Boolean = closeCount == 0

    override fun close() {
        closings.incrementAndGet()
        check(!failOnClose) { "The index failed to close, as the test asked." }
    }
}
