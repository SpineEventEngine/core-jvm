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

import com.google.common.base.Throwables
import io.spine.base.Error
import io.spine.protobuf.pack
import io.spine.server.commandbus.InvalidCommandException
import io.spine.server.entity.InvalidEntityStateException
import io.spine.server.event.InvalidEventException
import io.spine.validation.ConstraintViolation
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
    private val safePlaceholders: Set<String> = listOf(
        FIELD_PATH, FIELD_TYPE, MESSAGE_TYPE, PARENT_TYPE,
        REGEX_PATTERN, REGEX_MODIFIERS, GOES_COMPANION, GROUP_PATH, REQUIRE_FIELDS,
        RANGE_VALUE, MAX_VALUE, MAX_OPERATOR, MIN_VALUE, MIN_OPERATOR,
    ).mapTo(HashSet()) { it.value.name }

    /**
     * The names of the exceptions whose messages contain the values that failed validation.
     */
    private val validationExceptionNames: Set<String> = listOf(
        ValidationException::class.java,
        InvalidEntityStateException::class.java,
        InvalidCommandException::class.java,
        InvalidEventException::class.java,
    ).mapTo(HashSet()) { it.name }

    /**
     * A line of a stack trace that tells a stack frame, or how many frames are omitted.
     */
    private val frameLine = Regex("""^\s+(?:at |\.\.\. \d+ more)""")

    /**
     * The header line of a cause or of a suppressed exception: the class and the message.
     */
    private val causeHeader = Regex("""^(\s*(?:Caused by: |Suppressed: )[\w.$]+)(?:: .*)?$""")

    /**
     * Masks the values of the given event that may come from the messages of requests.
     */
    fun masked(event: DiagnosticEvent): DiagnosticEvent = when (event) {
        is RequestValidationFailed -> maskedValidation(event)
        is ConstraintViolated -> event.toBuilder()
            .clearViolation()
            .addAllViolation(event.violationList.map(::masked))
            .build()
        is RequestDenied ->
            if (event.hasError()) event.toBuilder().setError(masked(event.error)).build() else event
        is RequestProcessingFailed ->
            if (event.hasError()) event.toBuilder().setError(masked(event.error)).build() else event
        is HandlerFailedUnexpectedly ->
            if (event.hasError()) event.toBuilder().setError(masked(event.error)).build() else event
        is RoutingFailed ->
            if (event.hasError()) event.toBuilder().setError(masked(event.error)).build() else event
        is AggregateHistoryCorrupted ->
            if (event.hasError()) event.toBuilder().setError(masked(event.error)).build() else event
        else -> event
    }

    private fun maskedValidation(event: RequestValidationFailed): RequestValidationFailed {
        val builder = event.toBuilder()
            .clearViolation()
            .addAllViolation(event.violationList.map(::masked))
        if (event.hasError()) {
            builder.error = masked(event.error)
        }
        return builder.build()
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
     * The message of such an error, and the messages of the exceptions in its stack trace,
     * contain the invalid values. The messages are masked, and only the stack frames and
     * the classes of the exceptions remain in the stack trace. The violations in the details
     * of the error are masked one by one.
     *
     * Other errors are returned as they are.
     */
    fun masked(error: Error): Error {
        if (!error.isCausedByValidation()) {
            return error
        }
        val builder = error.toBuilder()
            .setStacktrace(maskedTrace(error.stacktrace))
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

    /**
     * Tells if the given throwable, or any of its causes, has a message with the values that
     * failed validation.
     */
    fun involvesValidation(failure: Throwable): Boolean =
        Throwables.getCausalChain(failure).any {
            it is ValidationException
                    || it is InvalidCommandException
                    || it is InvalidEventException
        }

    private fun Error.isCausedByValidation(): Boolean =
        details.`is`(ValidationError::class.java)
                || type in validationExceptionNames
                || validationExceptionNames.any { stacktrace.contains(it) }

    /**
     * Keeps the stack frames and the classes of the exceptions of the given stack trace.
     *
     * The messages of the exceptions are masked. The lines of a message that spans several
     * lines are dropped.
     */
    private fun maskedTrace(trace: String): String = trace.lineSequence()
        .mapIndexedNotNull { index, line ->
            when {
                line.isBlank() -> null
                frameLine.containsMatchIn(line) -> line
                index == 0 -> maskedHeader(line)
                else -> causeHeader.matchEntire(line)?.let { "${it.groupValues[1]}: $MASK" }
            }
        }
        .joinToString(System.lineSeparator())

    private fun maskedHeader(line: String): String =
        if (line.contains(": ")) "${line.substringBefore(": ")}: $MASK" else line
}
