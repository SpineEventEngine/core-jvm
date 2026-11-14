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

package io.spine.server.given

import io.spine.core.TenantId
import io.spine.server.tenant.TenantIndex

/**
 * The tenant index that fails to tell if it has a tenant.
 */
internal object FailingIndex : TenantIndex {

    /**
     * The message of the failure.
     */
    const val MESSAGE = "The tenant index is not available."

    override fun keep(id: TenantId) = Unit

    override fun contains(id: TenantId): Boolean = error(MESSAGE)

    override fun all(): Set<TenantId> = emptySet()

    override fun isOpen(): Boolean = true

    override fun close() = Unit
}
