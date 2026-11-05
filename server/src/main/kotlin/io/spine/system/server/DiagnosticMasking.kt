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

import io.spine.base.Error
import io.spine.protobuf.pack
import io.spine.validation.ConstraintViolation
import io.spine.validation.StandardPlaceholder
import io.spine.validation.StandardPlaceholder.FIELD_PATH
import io.spine.validation.StandardPlaceholder.FIELD_TYPE
import io.spine.validation.StandardPlaceholder.GOES_COMPANION
import io.spine.validation.StandardPlaceholder.GROUP_PATH
import io.spine.validation.StandardPlaceholder.MAX_OPERATOR
import io.spine.validation.StandardPlaceholder.MAX_VALUE
import io.spine.validation.StandardPlaceholder.MESSAGE_TYPE
import io.spine.validation.StandardPlaceholder.MIN_OPERATOR
import io.spine.validation.StandardPlaceholder.MIN_VALUE
import io.spine.validation.StandardPlaceholder.PARENT_TYPE
import io.spine.validation.StandardPlaceholder.RANGE_VALUE
import io.spine.validation.StandardPlaceholder.REGEX_MODIFIERS
import io.spine.validation.StandardPlaceholder.REGEX_PATTERN
import io.spine.validation.StandardPlaceholder.REQUIRE_FIELDS
import io.spine.validation.ValidationError
import io.spine.validation.ValidationException

/**
 * Masks the values that diagnostic events may carry from the messages of requests.
 *
 * A value that failed validation may be a secret, such as a password that does not match
 * a pattern. Such values come in the violations of validation constraints, and in an error
 * caused by a validation exception: in its message, in its stack trace, and in its details.
 *
 * Only the placeholders that describe a constraint, such as the path of a field or
 * the pattern it must match, keep their values in a violation message.
 */
internal object DiagnosticMasking {

    /**
     * The value written instead of a masked one.
     */
    internal const val MASK = "***"

    /**
     * The placeholders whose values describe a constraint, not the validated message.
     */
    private val safePlaceholders: Set<String> = listOf<StandardPlaceholder>(
        FIELD_PATH, FIELD_TYPE, MESSAGE_TYPE, PARENT_TYPE,
        REGEX_PATTERN, REGEX_MODIFIERS, GOES_COMPANION, GROUP_PATH, REQUIRE_FIELDS,
        RANGE_VALUE, MAX_VALUE, MAX_OPERATOR, MIN_VALUE, MIN_OPERATOR,
    ).mapTo(HashSet()) { it.value.name }

    private val validationException: String = ValidationException::class.java.name

    /**
     * The header line of an exception in a stack trace: the class and the message.
     */
    private val exceptionHeader = Regex(
        """^(\s*(?:Caused by: |Suppressed: )?[\w.$]+): .*$""",
        RegexOption.MULTILINE
    )

    /**
     * Masks the values of the given event that may come from the messages of requests.
     */
    fun masked(event: DiagnosticEvent): DiagnosticEvent = when (event) {
        is RequestValidationFailed -> event.toBuilder()
            .clearViolation()
            .addAllViolation(event.violationList.map(::masked))
            .setError(masked(event.error))
            .build()
        is ConstraintViolated -> event.toBuilder()
            .clearViolation()
            .addAllViolation(event.violationList.map(::masked))
            .build()
        is RequestDenied -> event.toBuilder().setError(masked(event.error)).build()
        is RequestProcessingFailed -> event.toBuilder().setError(masked(event.error)).build()
        is HandlerFailedUnexpectedly -> event.toBuilder().setError(masked(event.error)).build()
        is RoutingFailed -> event.toBuilder().setError(masked(event.error)).build()
        is AggregateHistoryCorrupted -> event.toBuilder().setError(masked(event.error)).build()
        else -> event
    }

    /**
     * Keeps only the type, the field path, and the message of the given violation,
     * with the values of the placeholders that may come from the message masked.
     */
    fun masked(violation: ConstraintViolation): ConstraintViolation {
        val message = violation.message
        val values = message.placeholderValueMap.mapValues { (placeholder, value) ->
            if (placeholder in safePlaceholders) value else MASK
        }
        val maskedMessage = message.toBuilder()
            .clearPlaceholderValue()
            .putAllPlaceholderValue(values)
        return ConstraintViolation.newBuilder()
            .setTypeName(violation.typeName)
            .setFieldPath(violation.fieldPath)
            .setMessage(maskedMessage)
            .build()
    }

    /**
     * Masks an error caused by a failed validation.
     *
     * The message of such an error, and the messages in the headers of its stack trace,
     * contain the invalid values, so they are masked. The violations in its details are
     * [masked][masked] one by one. Other errors are returned as they are.
     */
    fun masked(error: Error): Error {
        if (!error.isCausedByValidation()) {
            return error
        }
        val stacktrace = error.stacktrace.replace(exceptionHeader) { "${it.groupValues[1]}: $MASK" }
        val builder = error.toBuilder()
            .setStacktrace(stacktrace)
        if (error.message.isNotEmpty()) {
            builder.message = MASK
        }
        if (error.details.`is`(ValidationError::class.java)) {
            val details = error.details.unpack(ValidationError::class.java)
            val maskedDetails = ValidationError.newBuilder()
                .addAllConstraintViolation(details.constraintViolationList.map(::masked))
                .build()
            builder.details = maskedDetails.pack()
        }
        return builder.build()
    }

    private fun Error.isCausedByValidation(): Boolean =
        details.`is`(ValidationError::class.java) || stacktrace.contains(validationException)
}
