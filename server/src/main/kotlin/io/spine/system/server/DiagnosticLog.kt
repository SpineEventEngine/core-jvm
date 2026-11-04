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

import io.spine.core.Subscribe
import io.spine.logging.Level
import io.spine.logging.LoggingFactory
import io.spine.server.event.AbstractEventSubscriber
import io.spine.type.toCompactJson
import io.spine.validation.ConstraintViolation
import io.spine.validation.StandardPlaceholder.FIELD_DUPLICATES
import io.spine.validation.StandardPlaceholder.FIELD_PROPOSED_VALUE
import io.spine.validation.StandardPlaceholder.FIELD_VALUE
import kotlin.time.DurationUnit.SECONDS

/**
 * Writes the diagnostic events of a Bounded Context to the server log.
 *
 * The System context of every Bounded Context registers its own instance. A service writes
 * an event directly with [write] when no Bounded Context serves the request, or when the
 * request fails unexpectedly.
 *
 * The logger is named after this class. Operators turn it off, change its level, or send it
 * to its own output through the configuration of the logging backend.
 *
 * Each event is written as one statement: a short message, and the event in JSON.
 * The statement never contains the message of a request. The values of fields which failed
 * validation are masked, because they may be secrets, such as a password that does not
 * match a pattern.
 *
 * Events of the same kind are written at most once a second, so that a client that repeats
 * an invalid request does not flood the log. The log reports how many events were skipped.
 *
 * @param source The name of the Bounded Context whose System context registers the log.
 */
internal class DiagnosticLog(private val source: String) : AbstractEventSubscriber() {

    @Subscribe
    internal fun on(event: RequestValidationFailed) =
        write(event, source)

    @Subscribe
    internal fun on(event: UnsupportedRequestReceived) =
        write(event, source)

    @Subscribe
    internal fun on(event: TenantCheckFailed) =
        write(event, source)

    @Subscribe
    internal fun on(event: RequestDenied) =
        write(event, source)

    @Subscribe
    internal fun on(event: UnknownSubscriptionRequested) =
        write(event, source)

    @Subscribe
    internal fun on(event: RequestProcessingFailed) =
        write(event, source)

    @Subscribe
    internal fun on(event: ConstraintViolated) =
        write(event, source)

    @Subscribe
    internal fun on(event: CannotDispatchDuplicateCommand) =
        write(event, source)

    @Subscribe
    internal fun on(event: CannotDispatchDuplicateEvent) =
        write(event, source)

    @Subscribe
    internal fun on(event: HandlerFailedUnexpectedly) =
        write(event, source)

    @Subscribe
    internal fun on(event: RoutingFailed) =
        write(event, source)

    internal companion object {

        /**
         * The value written instead of a masked one.
         */
        internal const val MASK = "***"

        private val logger = LoggingFactory.loggerFor(DiagnosticLog::class)

        /**
         * Placeholders of a violation message whose values come from the invalid message.
         */
        private val maskedPlaceholders = setOf(FIELD_VALUE, FIELD_PROPOSED_VALUE, FIELD_DUPLICATES)
            .map { it.value.name }
            .toSet()

        /**
         * Writes the given diagnostic event to the log.
         *
         * @param event The event to write.
         * @param source The name of the Bounded Context, or of the service, which did not
         *   accept the request.
         */
        @JvmStatic
        fun write(event: DiagnosticEvent, source: String) {
            val kind = Kind.of(event)
            logger.at(kind.level)
                .per(kind)
                .atMostEvery(1, SECONDS)
                .log { statementOf(event, source) }
        }

        /**
         * Obtains the level at which the given event is written.
         */
        internal fun levelOf(event: DiagnosticEvent): Level = Kind.of(event).level

        /**
         * Composes the statement written for the given event.
         *
         * The statement has a short message, the source, and the event in JSON with
         * the values of invalid fields masked.
         */
        internal fun statementOf(event: DiagnosticEvent, source: String): String {
            val masked = when (event) {
                is RequestValidationFailed -> masked(event)
                is ConstraintViolated -> masked(event)
                else -> event
            }
            return "${Kind.of(event).message} Source: `$source`. ${masked.toCompactJson()}"
        }

        private fun masked(event: RequestValidationFailed): RequestValidationFailed =
            event.toBuilder()
                .clearViolation()
                .addAllViolation(event.violationList.map(::masked))
                .build()

        private fun masked(event: ConstraintViolated): ConstraintViolated =
            event.toBuilder()
                .clearViolation()
                .addAllViolation(event.violationList.map(::masked))
                .build()

        private fun masked(violation: ConstraintViolation): ConstraintViolation {
            val message = violation.message
            val values = message.placeholderValueMap.mapValues { (placeholder, value) ->
                if (placeholder in maskedPlaceholders) MASK else value
            }
            val maskedMessage = message.toBuilder()
                .clearPlaceholderValue()
                .putAllPlaceholderValue(values)
            return violation.toBuilder()
                .clearFieldValue()
                .setMessage(maskedMessage)
                .build()
        }
    }

    /**
     * The kinds of diagnostic events, each with its own level and rate limit.
     */
    private enum class Kind(val level: Level, val message: String) {
        VALIDATION_FAILED(Level.WARNING, "A request failed validation."),
        UNSUPPORTED_REQUEST(Level.WARNING, "A request of an unsupported type was received."),
        TENANT_CHECK_FAILED(Level.WARNING, "The tenant of a request was not accepted."),
        REQUEST_DENIED(Level.WARNING, "A filter denied a request."),
        UNKNOWN_SUBSCRIPTION(Level.WARNING, "An unknown subscription was requested."),
        PROCESSING_FAILED(Level.ERROR, "A request failed unexpectedly."),
        CONSTRAINT_VIOLATED(Level.ERROR, "An entity state is invalid."),
        DUPLICATE_COMMAND(Level.WARNING, "A command was dispatched for the second time."),
        DUPLICATE_EVENT(Level.WARNING, "An event was dispatched for the second time."),
        HANDLER_FAILED(Level.ERROR, "A handler failed unexpectedly."),
        ROUTING_FAILED(Level.ERROR, "A signal could not be routed."),
        OTHER(Level.WARNING, "A diagnostic event was posted.");

        companion object {

            fun of(event: DiagnosticEvent): Kind = when (event) {
                is RequestValidationFailed -> VALIDATION_FAILED
                is UnsupportedRequestReceived -> UNSUPPORTED_REQUEST
                is TenantCheckFailed -> TENANT_CHECK_FAILED
                is RequestDenied -> REQUEST_DENIED
                is UnknownSubscriptionRequested -> UNKNOWN_SUBSCRIPTION
                is RequestProcessingFailed -> PROCESSING_FAILED
                is ConstraintViolated -> CONSTRAINT_VIOLATED
                is CannotDispatchDuplicateCommand -> DUPLICATE_COMMAND
                is CannotDispatchDuplicateEvent -> DUPLICATE_EVENT
                is HandlerFailedUnexpectedly -> HANDLER_FAILED
                is RoutingFailed -> ROUTING_FAILED
                else -> OTHER
            }
        }
    }
}
