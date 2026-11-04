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

package io.spine.system.server

import com.google.protobuf.StringValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.spine.logging.Level
import io.spine.protobuf.AnyPacker
import io.spine.string.templateString
import io.spine.testing.logging.LoggingTest
import io.spine.validation.ConstraintViolation
import io.spine.validation.StandardPlaceholder.FIELD_PATH
import io.spine.validation.StandardPlaceholder.FIELD_VALUE
import io.spine.validation.constraintViolation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.logging.Level as JulLevel

@DisplayName("`DiagnosticLog` should")
internal class DiagnosticLogSpec {

    @Test
    fun `write the events of rejected requests at the warning level`() {
        listOf(
            RequestValidationFailed.getDefaultInstance(),
            UnsupportedRequestReceived.getDefaultInstance(),
            TenantCheckFailed.getDefaultInstance(),
            RequestDenied.getDefaultInstance(),
            UnknownSubscriptionRequested.getDefaultInstance(),
            CannotDispatchDuplicateCommand.getDefaultInstance(),
            CannotDispatchDuplicateEvent.getDefaultInstance(),
        ).forEach {
            DiagnosticLog.levelOf(it) shouldBe Level.WARNING
        }
    }

    @Test
    fun `write the events of server faults at the error level`() {
        listOf(
            RequestProcessingFailed.getDefaultInstance(),
            HandlerFailedUnexpectedly.getDefaultInstance(),
            RoutingFailed.getDefaultInstance(),
            ConstraintViolated.getDefaultInstance(),
        ).forEach {
            DiagnosticLog.levelOf(it) shouldBe Level.ERROR
        }
    }

    @Test
    fun `name the source of the event`() {
        val statement = DiagnosticLog.statementOf(
            UnsupportedRequestReceived.getDefaultInstance(), SOURCE
        )

        statement shouldContain "Source: `$SOURCE`."
    }

    @Test
    fun `mask the values of the fields of a request which failed validation`() {
        val event = requestValidationFailed {
            violation += passwordViolation
        }

        val statement = DiagnosticLog.statementOf(event, SOURCE)

        statement shouldNotContain SECRET
        statement shouldContain DiagnosticLog.MASK
        statement shouldContain FIELD
    }

    @Test
    fun `mask the values of the fields of an invalid entity state`() {
        val event = constraintViolated {
            violation += passwordViolation
        }

        val statement = DiagnosticLog.statementOf(event, SOURCE)

        statement shouldNotContain SECRET
        statement shouldContain FIELD
    }

    @Nested inner class
    `write an event to the log` : LoggingTest(DiagnosticLog::class.java, JulLevel.ALL) {

        @BeforeEach
        fun intercept() = interceptLogging()

        @AfterEach
        fun restore() = restoreLogging()

        @Test
        fun `at most once a second for events of a kind`() {
            // No other test writes this kind of event, so its rate limit is not used yet.
            val event = AggregateHistoryCorrupted.getDefaultInstance()

            DiagnosticLog.write(event, SOURCE)
            DiagnosticLog.write(event, SOURCE)

            assertLog().textOutput().contains("Source: `$SOURCE`.")
        }
    }

    private companion object {

        const val SOURCE = "Accounts"
        const val SECRET = "s3cr3t-passw0rd"
        const val FIELD = "password"

        val passwordViolation: ConstraintViolation = constraintViolation {
            typeName = "spine.test.SignUp"
            fieldValue = AnyPacker.pack(StringValue.of(SECRET))
            message = templateString {
                withPlaceholders = "The value `\${field.value}` of `\${field.path}` is invalid."
                placeholderValue.put(FIELD_PATH.value.name, FIELD)
                placeholderValue.put(FIELD_VALUE.value.name, SECRET)
            }
        }
    }
}
