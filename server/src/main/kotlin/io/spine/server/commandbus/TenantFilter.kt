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

package io.spine.server.commandbus

import io.spine.base.EventMessage
import io.spine.core.Ack
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.TenantId
import io.spine.protobuf.isNotDefault
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.WriteSideFunction
import io.spine.system.server.event.commandTenantNotExpected
import io.spine.system.server.event.commandTenantNotSpecified
import java.util.Optional

/**
 * A [CommandBus] filter that checks the tenant of a command.
 *
 * A multitenant bus does not accept a command without a tenant, and a single-tenant bus
 * does not accept a command with a tenant. Such a command is recorded in the System context
 * as [CommandTenantNotSpecified][io.spine.system.server.event.CommandTenantNotSpecified] or
 * [CommandTenantNotExpected][io.spine.system.server.event.CommandTenantNotExpected], and
 * refused with [TENANT_ERROR].
 *
 * @param multitenant Tells if the bus is multitenant.
 * @param writeSideFunction Obtains the System write side for a tenant.
 */
internal class TenantFilter(
    private val multitenant: Boolean,
    private val writeSideFunction: WriteSideFunction
) : CommandFilter {

    override fun filter(envelope: CommandEnvelope): Optional<Ack> {
        val tenant = envelope.tenantId()
        val problem = problemWith(envelope, tenant) ?: return letPass()
        writeSideFunction.get(tenant)
            .postEvent(problem, envelope.asMessageOrigin())
        return reject(envelope, TENANT_ERROR)
    }

    private fun problemWith(envelope: CommandEnvelope, tenant: TenantId): EventMessage? {
        val specified = tenant.isNotDefault()
        return when {
            multitenant && !specified -> commandTenantNotSpecified {
                id = envelope.id()
            }
            !multitenant && specified -> commandTenantNotExpected {
                id = envelope.id()
                this.tenant = tenant
            }
            else -> null
        }
    }
}
