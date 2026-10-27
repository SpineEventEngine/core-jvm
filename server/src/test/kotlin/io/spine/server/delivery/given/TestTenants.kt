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

package io.spine.server.delivery.given

import io.spine.core.TenantId
import io.spine.core.tenantId

/**
 * The tenants of the catch-up tests that use multi-tenant Bounded Contexts.
 */
internal object TestTenants {

    val X: TenantId = tenantId { value = "tenant-x" }
    val Y: TenantId = tenantId { value = "tenant-y" }
    val Z: TenantId = tenantId { value = "tenant-z" }
}
