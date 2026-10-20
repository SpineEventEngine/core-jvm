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

    @Test
    fun `reject a catch-up of a repository not registered in a Bounded Context`() {
        val unregistered = TestProjectionRepository()
        val past = subtract(currentTime(), fromMinutes(1))
        shouldThrow<IllegalStateException> {
            unregistered.catchUp(past, null)
        }
    }
}
