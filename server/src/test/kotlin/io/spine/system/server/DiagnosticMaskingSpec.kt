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
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Errors
import io.spine.base.error
import io.spine.protobuf.AnyPacker
import io.spine.server.entity.InvalidEntityStateException
import io.spine.string.templateString
import io.spine.type.toCompactJson
import io.spine.validation.ConstraintViolation
import io.spine.validation.StandardPlaceholder.FIELD_PATH
import io.spine.validation.StandardPlaceholder.FIELD_VALUE
import io.spine.validation.StandardPlaceholder.REGEX_PATTERN
import io.spine.validation.ValidationError
import io.spine.validation.ValidationException
import io.spine.validation.constraintViolation
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DiagnosticMasking` should")
internal class DiagnosticMaskingSpec {

    @Test
    fun `mask the value and the placeholders that may come from the message`() {
        val masked = DiagnosticMasking.masked(passwordViolation)

        masked.hasFieldValue() shouldBe false
        val values = masked.message.placeholderValueMap
        values[FIELD_VALUE.value.name] shouldBe DiagnosticMasking.MASK
        values[CUSTOM_PLACEHOLDER] shouldBe DiagnosticMasking.MASK
    }

    @Test
    fun `keep the placeholders that describe the constraint`() {
        val masked = DiagnosticMasking.masked(passwordViolation)

        val values = masked.message.placeholderValueMap
        values[FIELD_PATH.value.name] shouldBe FIELD
        values[REGEX_PATTERN.value.name] shouldBe PATTERN
        masked.typeName shouldBe passwordViolation.typeName
        masked.message.withPlaceholders shouldBe passwordViolation.message.withPlaceholders
    }

    @Test
    fun `mask the violations of an invalid entity state`() {
        val event = constraintViolated {
            violation += passwordViolation
        }

        val masked = DiagnosticMasking.masked(event)

        masked.toCompactJson() shouldNotContain SECRET
    }

    @Test
    fun `mask the message, the stack trace, and the details of an error caused by validation`() {
        val error = Errors.fromThrowable(ValidationException(passwordViolation))
        error.message shouldContain SECRET // Ensures the test checks the masking.

        val masked = DiagnosticMasking.masked(error)

        masked.message shouldBe DiagnosticMasking.MASK
        masked.stacktrace shouldNotContain SECRET
        masked.stacktrace shouldContain ValidationException::class.java.name
        val details = masked.details.unpack(ValidationError::class.java)
        details.toCompactJson() shouldNotContain SECRET
    }

    @Test
    fun `mask an error caused by validation in the events that carry errors`() {
        val cause = ValidationException(passwordViolation)
        val error = Errors.fromThrowable(IllegalStateException(cause))
        val event = handlerFailedUnexpectedly {
            this.error = error
        }

        val masked = DiagnosticMasking.masked(event)

        masked.shouldBeInstanceOf<HandlerFailedUnexpectedly>()
        masked.toCompactJson() shouldNotContain SECRET
    }

    @Test
    fun `mask every line of a message with several violations`() {
        val exception = ValidationException(listOf(passwordViolation, pinViolation))
        val error = Errors.fromThrowable(exception)
        error.stacktrace shouldContain PIN // Ensures the second line is in the stack trace.

        val masked = DiagnosticMasking.masked(error)

        masked.stacktrace shouldNotContain SECRET
        masked.stacktrace shouldNotContain PIN
        masked.stacktrace shouldContain "\tat "
    }

    @Test
    fun `mask an error caused by a subclass of a validation exception`() {
        val wrapped = "${InvalidEntityStateException::class.java.name}: $SECRET"
        val error = error {
            type = IllegalStateException::class.java.name
            message = wrapped
            stacktrace = "java.lang.IllegalStateException: $wrapped\n" +
                    "\tat io.spine.test.Handler.on(Handler.java:42)\n" +
                    "Caused by: $wrapped\n" +
                    "\t... 1 more"
        }

        val masked = DiagnosticMasking.masked(error)

        masked.message shouldBe DiagnosticMasking.MASK
        masked.stacktrace shouldNotContain SECRET
        masked.stacktrace shouldContain
                "Caused by: ${InvalidEntityStateException::class.java.name}"
    }

    @Test
    fun `leave an event without an error as it is`() {
        val event = HandlerFailedUnexpectedly.getDefaultInstance()

        val masked = DiagnosticMasking.masked(event).shouldBeInstanceOf<HandlerFailedUnexpectedly>()

        masked.hasError() shouldBe false
    }

    @Test
    fun `tell if a failure involves validation`() {
        val wrapped = IllegalStateException(ValidationException(passwordViolation))

        DiagnosticMasking.involvesValidation(wrapped) shouldBe true
        val other = IllegalStateException("Storage failed.")
        DiagnosticMasking.involvesValidation(other) shouldBe false
    }

    @Test
    fun `leave other errors as they are`() {
        val error = error {
            type = "com.example.AccessDenied"
            message = "The user has no access to the project."
        }

        DiagnosticMasking.masked(error) shouldBe error
    }

    private companion object {

        const val SECRET = "s3cr3t-passw0rd"
        const val FIELD = "password"
        const val PATTERN = "[a-z]+"
        const val CUSTOM_PLACEHOLDER = "custom.value"
        const val PIN = "9876"

        val passwordViolation: ConstraintViolation = constraintViolation {
            typeName = "spine.test.SignUp"
            fieldValue = AnyPacker.pack(StringValue.of(SECRET))
            message = templateString {
                withPlaceholders = "The value `\${field.value}` of `\${field.path}` must match" +
                        " `\${regex.pattern}`; `\${custom.value}`."
                placeholderValue.put(FIELD_PATH.value.name, FIELD)
                placeholderValue.put(FIELD_VALUE.value.name, SECRET)
                placeholderValue.put(REGEX_PATTERN.value.name, PATTERN)
                placeholderValue.put(CUSTOM_PLACEHOLDER, SECRET)
            }
        }

        val pinViolation: ConstraintViolation = constraintViolation {
            typeName = "spine.test.SignUp"
            fieldValue = AnyPacker.pack(StringValue.of(PIN))
            message = templateString {
                withPlaceholders = "The value `\${field.value}` of `\${field.path}` is too short."
                placeholderValue.put(FIELD_PATH.value.name, "pin")
                placeholderValue.put(FIELD_VALUE.value.name, PIN)
            }
        }
    }
}
