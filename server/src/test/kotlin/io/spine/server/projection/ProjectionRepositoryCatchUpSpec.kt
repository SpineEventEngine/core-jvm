/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.projection

import com.google.protobuf.util.Durations.fromMinutes
import com.google.protobuf.util.Timestamps.add
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.throwables.shouldThrow
import io.spine.base.Time.currentTime
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.projection.given.ProjectionRepositoryTestEnv.TestProjectionRepository
import io.spine.test.projection.ProjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ProjectionRepository` catch-up should")
internal class ProjectionRepositoryCatchUpSpec {

    private lateinit var context: BoundedContext
    private lateinit var repository: TestProjectionRepository

    @BeforeEach
    fun setUp() {
        context = BoundedContextBuilder.assumingTests().build()
        repository = TestProjectionRepository()
        context.internalAccess()
            .register(repository)
    }

    @AfterEach
    fun tearDown() {
        context.close()
    }

    @Test
    fun `reject a start time in the future`() {
        val future = add(currentTime(), fromMinutes(1))
        shouldThrow<IllegalArgumentException> {
            repository.catchUp(future, null)
        }
    }

    @Test
    fun `reject an empty set of target identifiers`() {
        val past = subtract(currentTime(), fromMinutes(1))
        shouldThrow<IllegalArgumentException> {
            repository.catchUp(past, emptySet())
        }
    }
}
