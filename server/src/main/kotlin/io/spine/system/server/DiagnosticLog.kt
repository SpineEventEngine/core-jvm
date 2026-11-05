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

import io.spine.annotation.VisibleForTesting
import io.spine.core.Subscribe
import io.spine.logging.Level
import io.spine.logging.LoggingFactory
import io.spine.server.event.AbstractEventSubscriber
import io.spine.type.toCompactJson
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
 * The statement never contains the message of a request. The values that failed validation
 * are [masked][DiagnosticMasking], because they may be secrets, such as a password that does
 * not match a pattern.
 *
 * Events of the same kind are written at most once a second, so that a client that repeats
 * an invalid request does not flood the log. The log reports how many events were skipped.
 *
 * @param source The name of the System context that registers the log.
 */
@Suppress("TooManyFunctions") // One subscriber method for each type of diagnostic event.
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

    @Subscribe
    internal fun on(event: AggregateHistoryCorrupted) =
        write(event, source)

    internal companion object {

        private val logger = LoggingFactory.loggerFor(DiagnosticLog::class)

        /**
         * Writes the given diagnostic event to the log.
         *
         * @param event The event to write.
         * @param source The name of the System context, or of the service, that writes
         *   the event.
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
         * Posts the given diagnostic event, or writes it to the log if posting fails.
         *
         * A failure of the System context must not change what the poster of a request
         * receives, so the failure is logged and the event is written directly.
         * The cause of the failure is logged unless its message may contain the values
         * that failed validation.
         *
         * @param event The event to post.
         * @param source The name of the bus or the service that posts the event.
         * @param post The action that posts the event to the System context.
         */
        @JvmStatic
        @Suppress("TooGenericExceptionCaught") // Any failure of the System context.
        fun postOrWrite(event: DiagnosticEvent, source: String, post: Runnable) {
            try {
                post.run()
            } catch (e: RuntimeException) {
                val api = logger.atError()
                val cause = if (DiagnosticMasking.involvesValidation(e)) null else e
                api.withCause(cause)
                    .log { "`$source` could not post a diagnostic event to the System context." }
                write(event, source)
            }
        }

        /**
         * Obtains the level at which the given event is written.
         */
        @VisibleForTesting
        internal fun levelOf(event: DiagnosticEvent): Level = Kind.of(event).level

        /**
         * Composes the statement written for the given event.
         *
         * The statement has a short message, the source, and the event in JSON with
         * the values of invalid fields masked.
         */
        internal fun statementOf(event: DiagnosticEvent, source: String): String {
            val masked = DiagnosticMasking.masked(event)
            return "${Kind.of(event).message} Source: `$source`. ${masked.toCompactJson()}"
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
        HISTORY_CORRUPTED(Level.ERROR, "The history of an aggregate could not be loaded."),
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
                is AggregateHistoryCorrupted -> HISTORY_CORRUPTED
                else -> OTHER
            }
        }
    }
}
