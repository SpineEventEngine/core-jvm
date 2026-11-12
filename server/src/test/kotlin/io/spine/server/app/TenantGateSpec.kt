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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.boundedContextName
import io.spine.core.tenantId
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.WARNING
import io.spine.server.Application
import io.spine.server.BoundedContextBuilder
import io.spine.server.given.FailingIndex
import io.spine.server.given.RecordingLog
import io.spine.server.tenant.TenantIndex
import io.spine.test.projection.Project
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.server.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@DisplayName("`TenantGate` should")
internal class TenantGateSpec {

    private val log = RecordingLog()
    private val tenant = tenantId { value = "Tenant of the request" }
    private val context = boundedContextName { value = "Serving" }
    private val request = refusedRequest {
        query = TestActorRequestFactory(TenantGateSpec::class.java, tenant).query()
            .all(Project::class.java)
    }
    private lateinit var application: Application

    @AfterEach
    fun closeApplication() {
        if (::application.isInitialized) {
            application.close()
        }
    }

    @Test
    fun `admit a registered tenant, describing nothing`() {
        val gate = gate()
        application.tenantIndex().keep(tenant)

        gate.check(tenant, context) { fail("The request must not be described.") }
            .shouldBeNull()

        log.records.shouldBeEmpty()
    }

    @Test
    fun `refuse a tenant that is not registered, and write the request to the log`() {
        val gate = gate()

        gate.check(tenant, context) { request } shouldBe TENANT_ERROR

        val (level, event) = log.single<RequestOfUnregisteredTenantRejected>()
        level shouldBe WARNING
        event.request shouldBe request
        event.tenant shouldBe tenant
        event.context shouldBe context
        log.records.single().message shouldBe null
        event.reason shouldContain "`TestService`"
    }

    @Test
    fun `leave the context unset for a request that every context serves`() {
        val gate = gate()

        gate.check(tenant, null) { request } shouldBe TENANT_ERROR

        val (_, event) = log.single<RequestOfUnregisteredTenantRejected>()
        event.hasContext() shouldBe false
    }

    @Test
    fun `answer the internal error if the index fails, and write the failure to the log`() {
        val gate = gate(FailingIndex)

        gate.check(tenant, context) { request } shouldBe INTERNAL_ERROR

        val (level, event) = log.single<TenantCheckFailed>()
        level shouldBe ERROR
        event.request shouldBe request
        event.tenant shouldBe tenant
        event.context shouldBe context
        event.error.message shouldBe FailingIndex.MESSAGE
        event.reason shouldContain FailingIndex.MESSAGE
    }

    /**
     * Creates the gate of an application of one multi-tenant context, which writes to [log].
     */
    private fun gate(index: TenantIndex? = null): TenantGate {
        val app = testApplication {
            applicationLog = log
            index?.let { tenantIndex = it }
            add(BoundedContextBuilder.assumingTests(/* multiTenant = */ true))
        }
        application = app
        return TenantGate(app, "TestService")
    }
}
