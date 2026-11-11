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

import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.tenantId
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.MemoizingWriteSide
import io.spine.system.server.WriteSideFunction.delegatingTo
import io.spine.system.server.event.CommandTenantNotExpected
import io.spine.system.server.event.CommandTenantNotSpecified
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`TenantFilter` should")
internal class TenantFilterSpec {

    private val writeSide = MemoizingWriteSide.singleTenant()

    @Nested inner class
    `in a multi-tenant bus` {

        private val filter = TenantFilter(multiTenant = true, delegatingTo(writeSide))

        @Test
        fun `refuse a command without a tenant and record it`() {
            val command = commandOf(TestActorRequestFactory(javaClass))

            val ack = filter.filter(command).orElseThrow()

            ack.status.errorCase shouldBe TENANT_ERROR
            val event = writeSide.lastSeenEvent().message()
                .shouldBeInstanceOf<CommandTenantNotSpecified>()
            event.id shouldBe command.id()
        }

        @Test
        fun `let a command with a tenant pass`() {
            val command = commandOf(TestActorRequestFactory(javaClass, tenant))

            filter.filter(command).shouldBeEmpty()
            writeSide.hasSeenEvents() shouldBe false
        }
    }

    @Nested inner class
    `in a single-tenant bus` {

        private val filter = TenantFilter(multiTenant = false, delegatingTo(writeSide))

        @Test
        fun `refuse a command with a tenant and record the tenant`() {
            val command = commandOf(TestActorRequestFactory(javaClass, tenant))

            val ack = filter.filter(command).orElseThrow()

            ack.status.errorCase shouldBe TENANT_ERROR
            val event = writeSide.lastSeenEvent().message()
                .shouldBeInstanceOf<CommandTenantNotExpected>()
            event.id shouldBe command.id()
            event.tenant shouldBe tenant
        }

        @Test
        fun `let a command without a tenant pass`() {
            val command = commandOf(TestActorRequestFactory(javaClass))

            filter.filter(command).shouldBeEmpty()
            writeSide.hasSeenEvents() shouldBe false
        }
    }

    private companion object {

        val tenant = tenantId { value = "TenantFilterSpec" }

        fun commandOf(requests: TestActorRequestFactory): CommandEnvelope =
            CommandEnvelope.of(requests.generateCommand())
    }
}
