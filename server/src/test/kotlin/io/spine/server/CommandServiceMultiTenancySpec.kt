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

package io.spine.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.Status.StatusCase.ERROR_CASE
import io.spine.protobuf.isNotDefault
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CommandService` in a multi-tenant context should")
class CommandServiceMultiTenancySpec : CommandServiceTenancyTest() {

    override fun createContext(): BoundedContext {
        val ctx = BoundedContextBuilder.assumingTests("Projects", /* multiTenant = */ true).build()
        ctx.register(Given.ProjectAggregateRepository())
        return ctx
    }

    @Test
    fun `return the tenant error case for a command without 'TenantId'`() {
        val command = Given.ACommand.createProject()
        val actorContext = command.context
            .actorContext()
            .toBuilder()
            .clearTenantId()
            .build()
        val commandContext = command.toBuilder()
            .contextBuilder
            .setActorContext(actorContext)
            .build()
        val commandWithoutTenant = command.toBuilder()
            .setContext(commandContext)
            .build()

        service.post(commandWithoutTenant, responseObserver)

        responseObserver.isCompleted shouldBe true

        val result = responseObserver.firstResponse()

        result shouldNotBe null

        result.isNotDefault() shouldBe true
        result.status.statusCase shouldBe ERROR_CASE
        result.status.errorCase shouldBe TENANT_ERROR
    }
}
