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

import io.spine.base.Error
import io.spine.base.error
import io.spine.core.CommandValidationError
import io.spine.core.CommandValidationError.DUPLICATE_COMMAND_VALUE
import io.spine.core.EventValidationError
import io.spine.core.EventValidationError.DUPLICATE_EVENT_VALUE
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope

/**
 * This guard ensures that a signal was not yet dispatched to the [entity][SignalDispatchingEntity].
 *
 * The check scans the entity's recent event history, which includes the events
 * committed by the earlier dispatches of the current delivery batch even before they
 * reach the durable journal — so a duplicate arriving within one batch is caught too.
 *
 * @param entity The entity to be guarded against double dispatch.
 */
internal class DoubleDispatchGuard(private val entity: SignalDispatchingEntity<*, *, *>) {

    /**
     * Whether this history-backed guard is active.
     *
     * Off by default: deduplication is primarily the delivery layer's responsibility, and this
     * guard is an opt-in per-repository backstop (see
     * [io.spine.server.aggregate.AggregateRepository.useDoubleDispatchGuard]).
     */
    private var enabled = false

    /**
     * The number of the most recent events scanned for a duplicate.
     *
     * Zero while the guard is disabled: both fields are assigned together
     * in [enable], and the depth is never read before that.
     */
    private var historyDepth = 0

    /**
     * Enables the guard, scanning up to [historyDepth] most recent events for a
     * duplicate on each dispatch.
     */
    fun enable(historyDepth: Int) {
        require(historyDepth > 0) { "History depth must be positive. Got $historyDepth." }
        this.enabled = true
        this.historyDepth = historyDepth
    }

    /**
     * Checks that the command was not dispatched to the entity.
     *
     * @param command An envelope with a command to check.
     * @return Duplicate command error if the command has been recently handled,
     *   `null` otherwise.
     */
    fun check(command: CommandEnvelope): Error? {
        if (!enabled || !didHandleRecently(command)) {
            return null
        }
        return error {
            type = CommandValidationError::class.java.simpleName
            code = DUPLICATE_COMMAND_VALUE
            message = "Command ${command.messageClass()}[${command.id().value()}] is a duplicate."
        }
    }

    /**
     * Checks that the event was not dispatched to the entity.
     *
     * @param event An envelope with an event to check.
     * @return Duplicate event error if the event has been recently handled,
     *   `null` otherwise.
     */
    fun check(event: EventEnvelope): Error? {
        if (!enabled || !didHandleRecently(event)) {
            return null
        }
        return error {
            type = EventValidationError::class.java.simpleName
            code = DUPLICATE_EVENT_VALUE
            message = "Event ${event.messageClass()}[${event.id().value()}] is a duplicate."
        }
    }

    /**
     * Checks if the event was already handled by the entity recently.
     *
     * The check is performed by searching the [historyDepth] most recent
     * events of the entity's history for an event caused by this event.
     *
     * This functionality supports the ability to stop duplicate events from being
     * dispatched to the entity.
     *
     * @param event The event to check.
     * @return `true` if the event was handled within the recent-history window,
     *   `false` otherwise.
     */
    private fun didHandleRecently(event: EventEnvelope): Boolean {
        val eventId = event.id()
        return entity.recentEventHistory()
            .read(historyDepth)
            .asSequence()
            .any {
                val origin = it.context().pastMessage.messageId()
                origin.isEvent && origin.asEventId() == eventId
            }
    }

    /**
     * Checks if the command was already handled by the entity recently.
     *
     * The check is performed by searching the [historyDepth] most recent
     * events of the entity's history for an event caused by this command.
     *
     * This functionality supports the ability to stop duplicate commands from being
     * dispatched to the entity.
     *
     * @param command The command to check.
     * @return `true` if the command was handled within the recent-history window,
     *   `false` otherwise.
     */
    private fun didHandleRecently(command: CommandEnvelope): Boolean {
        val commandId = command.id()
        return entity.recentEventHistory()
            .read(historyDepth)
            .asSequence()
            .any {
                val origin = it.context().pastMessage.messageId()
                origin.isCommand && origin.asCommandId() == commandId
            }
    }
}
