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

import io.kotest.matchers.shouldBe
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.LoginStatus
import io.spine.test.client.users.event.UserLoggedIn
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `ClientSpec` with the closing of a client that has active subscriptions.
 *
 * Unlike the other client specs, the client waits for its calls to end when it closes,
 * for as long as [Client.DEFAULT_SHUTDOWN_TIMEOUT].
 */
@DisplayName("`Client`, with active subscriptions, should")
internal class ClientCloseSpec : AbstractClientTest() {

    override fun contexts(): List<BoundedContextBuilder> = listOf(ClientTestContext.users())

    override fun newClientBuilder(serverName: String): Client.Builder =
        Client.inProcess(serverName)

    @Test
    fun `close after the server ends the streams of its subscriptions`() {
        val client = client()
        client.asGuest().subscribeToEvent(UserLoggedIn::class.java).observe { }.post()
        client.asGuest().subscribeTo(LoginStatus::class.java).observe { }.post()

        client.close()

        client.isOpen shouldBe false
        client.subscriptions().isEmpty shouldBe true
    }
}
