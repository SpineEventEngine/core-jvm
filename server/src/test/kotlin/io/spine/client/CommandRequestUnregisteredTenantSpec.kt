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

package io.spine.client

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.core.Command
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.tenantId
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.users.builder as users
import io.spine.test.client.users.command.LogInUser
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.core.given.GivenUserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CommandRequest` of a tenant not registered in the application should")
internal class CommandRequestUnregisteredTenantSpec : AbstractClientTest() {

    private val tenant = tenantId { value = "Tenant of the client" }

    override fun contexts(): List<BoundedContextBuilder> =
        listOf(users(multiTenant = true))

    override fun newClientBuilder(serverName: String): Client.Builder =
        super.newClientBuilder(serverName).forTenant(tenant)

    @Test
    fun `report the tenant error to the server error handler, posting nothing`() {
        var postedMessage: Any? = null
        var receivedErrorCase: ErrorCase? = null

        val subscriptions = client().asGuest()
            .command(logInUser())
            .observe(UserLoggedIn::class.java) { }
            .onServerError { message, errorCase ->
                postedMessage = message
                receivedErrorCase = errorCase
            }
            .post()

        postedMessage.shouldBeInstanceOf<Command>()
        receivedErrorCase shouldBe TENANT_ERROR
        subscriptions.shouldBeEmpty()
        client().subscriptions().isEmpty shouldBe true
    }

    @Test
    fun `subscribe and post once the tenant is registered`() {
        application().tenantIndex().keep(tenant)
        var receivedErrorCase: ErrorCase? = null

        val subscriptions = client().asGuest()
            .command(logInUser())
            .observe(UserLoggedIn::class.java) { }
            .onServerError { _, errorCase -> receivedErrorCase = errorCase }
            .post()

        receivedErrorCase shouldBe null
        subscriptions.shouldNotBeEmpty()
    }

    private fun logInUser(): LogInUser =
        LogInUser.newBuilder()
            .setUser(GivenUserId.generated())
            .build()
}
