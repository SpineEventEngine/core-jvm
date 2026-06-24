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

package io.spine.server.trace.otel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import io.spine.base.Identifier.newUuid
import io.spine.core.SignalId
import io.spine.core.commandId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trace ID derivation should")
internal class TraceIdsSpec {

    @Test
    fun `produce a 32-character hex trace ID`() {
        val id = signalId(newUuid())
        traceIdOf(id) shouldHaveLength 32
        traceIdOf(id) shouldMatch HEX
    }

    @Test
    fun `produce a 16-character hex span ID`() {
        val id = signalId(newUuid())
        spanIdOf(id) shouldHaveLength 16
        spanIdOf(id) shouldMatch HEX
    }

    @Test
    fun `be deterministic for the same root signal ID`() {
        val id = signalId(newUuid())
        traceIdOf(id) shouldBe traceIdOf(signalId(id.value()))
        spanIdOf(id) shouldBe spanIdOf(signalId(id.value()))
    }

    @Test
    fun `produce different trace IDs for different root signals`() {
        traceIdOf(signalId(newUuid())) shouldNotBe traceIdOf(signalId(newUuid()))
    }

    @Test
    fun `zero-pad each half of the UUID to 16 hex characters`() {
        val id = signalId("00000000-0000-0001-0000-000000000001")
        traceIdOf(id) shouldBe "00000000000000010000000000000001"
        spanIdOf(id) shouldBe "0000000000000001"
    }

    @Test
    fun `derive a stable trace ID even when the signal ID is not a UUID`() {
        val id = signalId("not-a-uuid")
        traceIdOf(id) shouldHaveLength 32
        traceIdOf(id) shouldMatch HEX
        traceIdOf(id) shouldBe traceIdOf(signalId("not-a-uuid"))
    }

    @Test
    fun `produce valid non-zero IDs for inputs that would otherwise fold to zero`() {
        // `"0".repeat(32)` folds to all-zero bytes; `"ab"` leaves the lower half zero.
        listOf("0".repeat(32), "ab").forEach { value ->
            val id = signalId(value)
            traceIdOf(id) shouldNotBe "0".repeat(32)
            spanIdOf(id) shouldNotBe "0".repeat(16)
        }
    }

    private fun signalId(value: String): SignalId = commandId { uuid = value }

    private companion object {
        val HEX = Regex("[0-9a-f]+")
    }
}
