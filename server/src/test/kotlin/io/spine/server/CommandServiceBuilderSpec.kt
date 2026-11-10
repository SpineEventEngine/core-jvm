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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldNotBe
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.CreateProjectAssignee
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CommandService.Builder` should")
internal class CommandServiceBuilderSpec {

    @Test
    fun `build a service of contexts that handle commands of distinct types`() {
        contextHandlingCreateProject().use { context ->
            CommandService.newBuilder()
                .add(context)
                .build() shouldNotBe null
        }
    }

    @Test
    fun `refuse two contexts that handle commands of one type`() {
        contextHandlingCreateProject().use { first ->
            contextHandlingCreateProject().use { second ->
                shouldThrow<IllegalStateException> {
                    CommandService.newBuilder()
                        .add(first)
                        .add(second)
                        .build()
                }
            }
        }
    }

    /**
     * Builds a context that handles the `CmdBusCreateProject` command.
     */
    private fun contextHandlingCreateProject(): BoundedContext =
        BoundedContextBuilder.assumingTests()
            .addCommandDispatcher(CreateProjectAssignee())
            .build()
}
