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
