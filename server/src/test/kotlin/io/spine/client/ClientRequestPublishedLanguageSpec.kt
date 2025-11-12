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
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.ClientTestContext
import io.spine.test.unpublished.Locomotive
import io.spine.test.unpublished.command.Halt
import io.spine.test.unpublished.event.WheelsKnocked
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.UnpublishedLanguageException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MuteLogging
@DisplayName("`ClientRequest` should prohibit using `internal_type` messages when")
internal class ClientRequestPublishedLanguageSpec : AbstractClientTest() {

    private lateinit var request: ClientRequest

    override fun contexts(): List<BoundedContextBuilder> =
        ImmutableList.of(ClientTestContext.users())

    @BeforeEach
    fun createRequest() {
        request = client().asGuest()
    }

    @Test
    fun `sending commands`() {
        val cmd = Halt.newBuilder().setValue(true).build()

        assertThrows<UnpublishedLanguageException> {
            request.command(cmd)
        }
    }

    @Test
    fun `subscribing to entity states`() {
        assertThrows<UnpublishedLanguageException> {
            request.subscribeTo(Locomotive::class.java)
        }
    }

    @Test
    fun `subscribing to events`() {
        assertThrows<UnpublishedLanguageException> {
            request.subscribeToEvent(WheelsKnocked::class.java)
        }
    }

    @Test
    fun `running queries`() {
        val query = Locomotive.query().name().`is`("M-497").build()
        assertThrows<UnpublishedLanguageException> {
            request.run(query)
        }
    }
}
