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

package io.spine.server.entity

import com.google.protobuf.Message
import com.google.protobuf.StringValue
import io.kotest.matchers.shouldBe
import io.spine.change.ValueMismatch
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.test.shared.EmptyEntity
import io.spine.server.type.CommandEnvelope
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
}
