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

package io.spine.server.procman

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldNotBe
import io.spine.server.BoundedContextBuilder
import io.spine.server.entity.given.Given
import io.spine.server.procman.given.pm.LastSignalMemo
import io.spine.test.procman.ElephantProcess
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ProcessManager` lifecycle should")
internal class ProcessManagerLifecycleSpec {

    private lateinit var processManager: LastSignalMemo

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        processManager = Given.processManagerOfClass(LastSignalMemo::class.java)
            .withId(LastSignalMemo.ID)
            .withVersion(1)
            .withState(ElephantProcess.getDefaultInstance())
            .build()
    }

    @Test
    fun `fail to select entities before being attached to a context`() {
        shouldThrow<IllegalStateException> {
            processManager.select(ElephantProcess::class.java)
        }
    }

    @Test
    fun `reject being attached to a Bounded Context twice`() {
        val context = BoundedContextBuilder.assumingTests().build()
        val another = BoundedContextBuilder.assumingTests().build()
        processManager.injectContext(context)

        shouldThrow<IllegalStateException> {
            processManager.injectContext(another)
        }

        context.close()
        another.close()
    }

    @Test
    fun `expose the classes of the events it produces`() {
        processManager.producedEvents() shouldNotBe null
    }
}
