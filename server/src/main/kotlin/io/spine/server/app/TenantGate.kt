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

package io.spine.server.app

import io.spine.base.Errors
import io.spine.core.BoundedContextName
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.TenantId
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.WARNING
import io.spine.server.Application

/**
 * Admits to the Bounded Contexts of an application only the requests of the tenants
 * registered in the application.
 *
 * Writes to the [log][Application.log] of the application each request that it refuses,
 * and each failure to tell if a tenant is registered.
 *
 * @param application The application whose tenants are admitted.
 * @param service The name of the service whose requests are checked, for the log.
 */
internal class TenantGate(
    private val application: Application,
    private val service: String
) {

    /**
     * Checks that the given tenant of a request is registered in the application.
     *
     * @param tenant The tenant of the request.
     * @param context The name of the Bounded Context that serves the request, or `null`
     *   if every context of the application serves it.
     * @param request Describes the request, for the log.
     * @return `null` if the tenant is registered, [TENANT_ERROR] if it is not, or
     *   [INTERNAL_ERROR] if the tenant index of the application fails to tell.
     */
    @Suppress("TooGenericExceptionCaught") // Any failure of the index is reported alike.
    fun check(
        tenant: TenantId,
        context: BoundedContextName?,
        request: () -> RefusedRequest
    ): ErrorCase? {
        val registered = try {
            application.tenantIndex().contains(tenant)
        } catch (e: RuntimeException) {
            return failed(tenant, context, request, e)
        }
        return if (registered) null else unregistered(tenant, context, request)
    }

    /**
     * Writes to the log that the tenant of the request is not registered.
     *
     * @return [TENANT_ERROR], to answer the request with.
     */
    private fun unregistered(
        tenant: TenantId,
        context: BoundedContextName?,
        request: () -> RefusedRequest
    ): ErrorCase {
        application.log().logEvent(WARNING) {
            requestOfUnregisteredTenantRejected {
                this.request = request()
                this.tenant = tenant
                context?.let { this.context = it }
                this.reason = "`$service` rejected the request, since its tenant is not" +
                        " registered in the application."
            }
        }
        return TENANT_ERROR
    }

    /**
     * Writes to the log that the tenant index failed to tell if it has the tenant
     * of the request.
     *
     * @return [INTERNAL_ERROR], to answer the request with.
     */
    private fun failed(
        tenant: TenantId,
        context: BoundedContextName?,
        request: () -> RefusedRequest,
        failure: RuntimeException
    ): ErrorCase {
        application.log().logEvent(ERROR) {
            tenantCheckFailed {
                this.request = request()
                this.tenant = tenant
                context?.let { this.context = it }
                this.error = Errors.fromThrowable(failure)
                this.reason = "`$service` failed the request, since the tenant index of" +
                        " the application could not tell if it has the tenant: ${failure.message}"
            }
        }
        return INTERNAL_ERROR
    }
}
