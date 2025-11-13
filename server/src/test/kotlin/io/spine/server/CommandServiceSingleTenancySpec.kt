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

package io.spine.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.core.Status.StatusCase.ERROR
import io.spine.protobuf.isNotDefault
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CommandService` in a single-tenant context should")
internal class CommandServiceSingleTenancySpec : CommandServiceTenancyTest() {

    override fun createContext(): BoundedContext {
        val ctx = BoundedContext.singleTenant("Projects").build()
        ctx.register(Given.ProjectAggregateRepository())
        return ctx
    }

    @Test
    fun `return ERROR for a command with 'TenantId'`() {
        // Creates a command with `TenantId`.
        val command = Given.ACommand.createProject()

        service.post(command, responseObserver)

        responseObserver.isCompleted shouldBe true

        val result = responseObserver.firstResponse()

        result shouldNotBe null

        result.isNotDefault() shouldBe true
        result.status.statusCase shouldBe ERROR
    }
}
