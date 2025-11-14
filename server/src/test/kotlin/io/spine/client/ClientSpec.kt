/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.client

import com.google.common.collect.ImmutableList
import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import io.kotest.matchers.shouldBe
import io.spine.client.EntityStateFilter.eq
import io.spine.client.EventFilter.eq
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.users.ActiveUsersProjection.THE_ID
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.ActiveUsers
import io.spine.test.client.users.LoginStatus
import io.spine.test.client.users.activeUsers
import io.spine.test.client.users.command.logInUser
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.test.client.users.event.UserLoggedOut
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`Client` should")
internal class ClientSpec : AbstractClientTest() {

    override fun contexts(): ImmutableList<BoundedContextBuilder> {
        return ImmutableList.of(ClientTestContext.users())
    }

    @Test
    fun `be opened upon creation`() {
        client().isOpen shouldBe true
    }

    @Test
    fun `close upon request`() {
        val client = client()
        client.close()
        client.isOpen shouldBe false
    }

    @Test
    fun `have 'shutdown()' alias method`() {
        val client = client()
        client.shutdown()
        client.isOpen shouldBe false
    }

    @Test
    fun `create requests on behalf of a user`() {
        val expected = GivenUserId.generated()
        val request = client().onBehalfOf(expected)
        request.user() shouldBe expected
    }

    @Test
    fun `create requests for a guest user`() {
        val request = client().asGuest()
        request.user() shouldBe Client.DEFAULT_GUEST_ID
    }

    @Nested
    internal inner class `Manage subscriptions` {

        private var subscriptions: MutableList<Subscription> = ArrayList()

        @BeforeEach
        fun createSubscriptions() {
            subscriptions.clear()
            val currentUser = GivenUserId.generated()
            val client = client()
            val userLoggedIn = client.onBehalfOf(currentUser)
                .subscribeToEvent(UserLoggedIn::class.java)
                .where(eq(UserLoggedIn.Field.user(), currentUser))
                .observe { _ -> }
                .post()
            val userLoggedOut = client.onBehalfOf(currentUser)
                .subscribeToEvent(UserLoggedOut::class.java)
                .where(eq(UserLoggedOut.Field.user(), currentUser))
                .observe { _ -> }
                .post()
            val loginStatus = client.onBehalfOf(currentUser)
                .subscribeTo(LoginStatus::class.java)
                .where(
                    eq(
                        LoginStatus.Field.userId(),
                        currentUser.value
                    )
                )
                .observe { _ -> }
                .post()

            subscriptions.addAll(listOf(
                userLoggedIn,
                userLoggedOut,
                loginStatus
            ))
        }

        @Test
        fun `remembering them until canceled`() {
            val client = client()
            val remembered = client.subscriptions()
            subscriptions.forEach { s ->
                remembered.contains(s) shouldBe true
            }
            subscriptions.forEach{ s ->
                remembered.cancel(s)
                remembered.contains(s) shouldBe false
            }
        }

        @Test
        fun `clear subscriptions when closing`() {
            val client = client()
            val subscriptions = client.subscriptions()
            this.subscriptions.forEach { s ->
                subscriptions.contains(s) shouldBe true
            }
            client.close()
            subscriptions.isEmpty shouldBe true
        }
    }

    @Nested
    @DisplayName("query")
    internal inner class Queries {

        @Test
        fun `entities by ID`() {
            val client = client()
            val user = GivenUserId.generated()
            val command = logInUser { this.user = user }
            client.asGuest()
                .command(command)
                .postAndForget()
            val query = ActiveUsers.query()
                .id().`is`(THE_ID)
                .build()
            val users = client.onBehalfOf(user).run(query)
            assertThat(users)
                .comparingExpectedFieldsOnly()
                .containsExactly(
                    activeUsers {
                        id = THE_ID
                        count = 1
                    })
        }
    }
}
