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

package io.spine.server.entity

import com.google.common.collect.ImmutableSet
import com.google.protobuf.Message
import com.google.protobuf.StringValue
import io.kotest.matchers.shouldBe
import io.spine.change.ValueMismatch
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.test.shared.EmptyEntity
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.testing.TestValues.newUuidValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`SignalDispatchingEntity` should")
internal class SignalDispatchingEntitySpec {

    private val entity = MismatchingEntity(javaClass.name)

    @Test
    fun `set own version to created mismatches`() {
        val version = entity.version().number

        entity.doExpectedDefault(msg(), msg()).version shouldBe version
        entity.doExpectedNotDefault(msg()).version shouldBe version
        entity.doExpectedNotDefault(msg(), msg()).version shouldBe version
        entity.doUnexpectedValue(msg(), msg(), msg()).version shouldBe version

        entity.doExpectedEmpty(str(), str()).version shouldBe version
        entity.doExpectedNotEmpty(str()).version shouldBe version
        entity.doUnexpectedValue(str(), str(), str()).version shouldBe version
    }
}

/**
 * Generates a `StringValue` based on a generated UUID.
 */
private fun msg(): StringValue = newUuidValue()

/**
 * Generates a `String` based on a generated UUID.
 */
private fun str(): String = msg().value

/**
 * A signal-dispatching entity re-exposing the `protected` mismatch factory methods
 * for the assertions of [SignalDispatchingEntitySpec].
 */
private class MismatchingEntity(id: String) :
    SignalDispatchingEntity<String, EmptyEntity, EmptyEntity.Builder>(id) {

    fun doExpectedDefault(actual: Message, newValue: Message): ValueMismatch =
        expectedDefault(actual, newValue)

    fun doExpectedNotDefault(expected: Message): ValueMismatch =
        expectedNotDefault(expected)

    fun doExpectedNotDefault(expected: Message, newValue: Message): ValueMismatch =
        expectedNotDefault(expected, newValue)

    fun doUnexpectedValue(expected: Message, actual: Message, newValue: Message): ValueMismatch =
        unexpectedValue(expected, actual, newValue)

    fun doExpectedEmpty(actual: String, newValue: String): ValueMismatch =
        expectedEmpty(actual, newValue)

    fun doExpectedNotEmpty(expected: String): ValueMismatch =
        expectedNotEmpty(expected)

    fun doUnexpectedValue(expected: String, actual: String, newValue: String): ValueMismatch =
        unexpectedValue(expected, actual, newValue)

    override fun dispatchCommand(command: CommandEnvelope): DispatchOutcome =
        DispatchOutcome.getDefaultInstance()

    override fun dispatchEvent(event: EventEnvelope): DispatchOutcome =
        DispatchOutcome.getDefaultInstance()

    override fun producedEvents(): ImmutableSet<EventClass> = ImmutableSet.of()
}
