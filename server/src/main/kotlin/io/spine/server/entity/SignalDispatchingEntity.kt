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

import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.Message
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.EntityState
import io.spine.base.Error
import io.spine.base.Identifier
import io.spine.change.MessageMismatch
import io.spine.change.StringMismatch
import io.spine.change.ValueMismatch
import io.spine.core.Event
import io.spine.core.MessageId
import io.spine.server.command.Assignee
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.dispatchOutcome
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.validation.ValidatingBuilder
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull

/**
 * Abstract base for entities that dispatch signals — both commands and events —
 * to their receptors.
 *
 * Such an entity is an [Assignee]: commands are dispatched to the methods
 * [assigned][io.spine.server.command.Assign] to handle them. Events are dispatched
 * to the receptors reacting to them.
 *
 * This is the entity-level counterpart of [SignalDispatchingRepository]: the repository routes
 * a signal to the target entity, and the entity dispatches it to the matching receptor.
 *
 * Because such an entity emits events, it keeps the [recent history][recentEventHistory] of
 * them, served lazily from the entity's durable journal through a repository-installed loader.
 *
 * ## Reporting value mismatches
 *
 * A command may carry an expectation about the current state of the entity — for example,
 * the value a field is believed to hold before it is changed or cleared. When a command
 * receptor discovers that the actual value differs from the expected one, it may report
 * the discrepancy — typically as a part of a rejection — with a `ValueMismatch` created
 * by one of the `expectedXxx`/`unexpectedXxx` helper methods.
 *
 * Each helper stamps the produced mismatch with the current version of this entity, telling
 * the client which version of the state contradicted the expectation, so that the conflict
 * can be resolved. The helpers belong to the `protected` API of this class because they
 * exist for the receptor bodies of the subclasses: they read the version of this very
 * entity, and have no use outside of its own signal-handling code.
 *
 * @param I The type of the entity identifiers.
 * @param S The type of the entity state.
 * @param B The type of the builders for the entity state.
 *
 * @see io.spine.server.command.Assign
 * @see SignalDispatchingRepository
 */
@Suppress("TooManyFunctions") // This base for signal-dispatching entities has many functions.
public abstract class SignalDispatchingEntity<I : Any,
                                              S : EntityState<I>,
                                              B : ValidatingBuilder<S>> :
    TransactionalEntity<I, S, B>,
    Assignee {

    private val recentEventHistory = RecentEventHistory()

    private val doubleDispatchGuard = DoubleDispatchGuard(this)

    private val uncommittedEventHistory = UncommittedEventHistory()

    /**
     * Cached value of the ID in the form of an `Any` instance.
     */
    private val idAsAny: ProtoAny by lazy { Identifier.pack(id) }

    /**
     * Creates a new instance with the entity ID left unassigned.
     *
     * When this constructor is called, the ID must be set before any other
     * interactions with the instance.
     */
    protected constructor() : super()

    /**
     * Creates a new instance with the passed ID.
     *
     * @param id The ID for the new instance.
     */
    protected constructor(id: I) : super(id)

    override fun producerId(): ProtoAny = idAsAny

    /**
     * Obtains the recent history of events of this entity.
     */
    protected open fun recentEventHistory(): RecentEventHistory = recentEventHistory

    /**
     * Installs the loader serving the [recent event history][recentEventHistory]
     * reads from the durable journal of this entity.
     *
     * Called by repositories when the entity is created or loaded, so that the recent
     * history survives the instance lifecycle instead of being limited to the events
     * committed by this very instance.
     */
    internal fun setEventHistoryLoader(loader: EventHistoryLoader) {
        recentEventHistory.useLoader(loader)
    }

    /**
     * Creates an iterator over up to [depth] most recent events of this entity's history,
     * newest first.
     *
     * Fewer events are returned if the history retains fewer. The events emitted by the
     * current, not-yet-committed dispatch are excluded. The events committed by the earlier
     * dispatches served by this instance — e.g., the preceding signals of a delivery batch —
     * are included even while the deferred journal write has not persisted them yet.
     *
     * @param depth The maximal number of the most recent events to return; must be positive.
     * @return New iterator instance.
     * @throws IllegalArgumentException If the [depth] is not positive.
     */
    protected fun eventHistoryBackward(depth: Int): Iterator<Event> =
        recentEventHistory().read(depth)

    /**
     * Verifies if up to [depth] most recent events of this entity's history contain an event
     * that satisfies the passed predicate.
     *
     * The visibility caveats of [eventHistoryBackward] apply to this check.
     *
     * @param depth The maximal number of the most recent events to inspect; must be positive.
     * @param predicate The predicate to test the events against.
     */
    protected fun eventHistoryContains(depth: Int, predicate: Predicate<Event>): Boolean =
        eventHistoryBackward(depth).asSequence().any { predicate.test(it) }

    /**
     * Records the events produced by the current dispatch so that they are stored into the
     * journal and made available as recent history.
     *
     * Called by the framework after a command or reaction has been dispatched and its
     * transaction committed. Rejection events are not journaled.
     *
     * The journaled events also enter the [recent event history][recentEventHistory] right
     * away, so the subsequent dispatches served by this instance — e.g., the later signals of
     * a delivery batch — read them without waiting for the journal write, which may be
     * deferred to the end of the batch.
     *
     * @param events The events emitted by the current command handler or reactor.
     */
    internal fun recordEvents(events: List<Event>) {
        val journaled = uncommittedEventHistory.record(events)
        recentEventHistory().append(journaled)
    }

    /**
     * Returns all uncommitted events.
     *
     * @return Immutable view of all uncommitted events.
     */
    @Internal
    @VisibleForTesting
    @JvmName("getUncommittedEvents") // Keeps the JVM name for the remaining Java test callers.
    internal fun getUncommittedEvents(): UncommittedEvents = uncommittedEventHistory.events()

    /**
     * Tells if there are any uncommitted events.
     */
    @Internal
    @JvmName("hasUncommittedEvents") // Keeps the JVM name for the remaining Java callers.
    internal fun hasUncommittedEvents(): Boolean = uncommittedEventHistory.hasEvents()

    /**
     * Returns the uncommitted events of this entity.
     */
    internal fun uncommittedEventHistory(): UncommittedEventHistory = uncommittedEventHistory

    /**
     * Marks the uncommitted events of this entity as committed and clears them.
     */
    @Internal
    @JvmName("commitEvents") // Keeps the JVM name for the remaining Java test callers.
    internal fun commitEvents() {
        uncommittedEventHistory.commit()
    }

    /**
     * Returns the guard against dispatching the same signal to this entity more than once.
     */
    protected fun doubleDispatchGuard(): DoubleDispatchGuard = doubleDispatchGuard

    /**
     * Checks the passed command against the [double-dispatch guard][doubleDispatchGuard].
     *
     * @param command The envelope with the command about to be dispatched.
     * @return The erroneous outcome to return instead of dispatching if the command was
     *   already dispatched to this entity, or `null` if the dispatch may proceed.
     */
    protected fun detectDuplicate(command: CommandEnvelope): DispatchOutcome? =
        doubleDispatchGuard.check(command).getOrNull()
            ?.let { error -> duplicateOutcome(command.messageId(), error) }

    /**
     * Checks the passed event against the [double-dispatch guard][doubleDispatchGuard].
     *
     * @param event The envelope with the event about to be dispatched.
     * @return The erroneous outcome to return instead of dispatching if the event was
     *   already dispatched to this entity, or `null` if the dispatch may proceed.
     */
    protected fun detectDuplicate(event: EventEnvelope): DispatchOutcome? =
        doubleDispatchGuard.check(event).getOrNull()
            ?.let { error -> duplicateOutcome(event.messageId(), error) }

    private fun duplicateOutcome(signal: MessageId, error: Error): DispatchOutcome =
        dispatchOutcome {
            propagatedSignal = signal
            this.error = error
        }

    /**
     * Enables the opt-in double-dispatch guard for this entity, scanning up to
     * [historyDepth] most recent events for a duplicate on each dispatch.
     */
    internal fun enableDoubleDispatchGuard(historyDepth: Int) {
        doubleDispatchGuard.enable(historyDepth)
    }

    /**
     * Dispatches the given command to the receptor assigned to handle it.
     *
     * Handling a command results in emitting one or more events, or a rejection,
     * described by the returned outcome.
     *
     * @param command The envelope with the command to dispatch.
     * @return The outcome of dispatching the command.
     */
    protected abstract fun dispatchCommand(command: CommandEnvelope): DispatchOutcome

    /**
     * Dispatches the given event to the receptor reacting to it.
     *
     * Reacting to an event may result in emitting one or more event messages, described by
     * the returned outcome.
     *
     * @param event The envelope with the event to dispatch.
     * @return The outcome of dispatching the event.
     */
    protected abstract fun dispatchEvent(event: EventEnvelope): DispatchOutcome

    /**
     * Creates a `ValueMismatch` for the case of discovering a non-default value
     * when the default value was expected by a command.
     *
     * @param actual The value discovered instead of the default value.
     * @param newValue The new value requested in the command.
     * @return A new `ValueMismatch` instance.
     */
    protected fun expectedDefault(actual: Message, newValue: Message): ValueMismatch =
        MessageMismatch.expectedDefault(actual, newValue, versionNumber())

    /**
     * Creates a `ValueMismatch` for a command that wanted to *clear* a value,
     * but discovered that the field already has the default value.
     *
     * @param expected The value of the field that the command wanted to clear.
     * @return A new `ValueMismatch` instance.
     */
    protected fun expectedNotDefault(expected: Message): ValueMismatch =
        MessageMismatch.expectedNotDefault(expected, versionNumber())

    /**
     * Creates a `ValueMismatch` for a command that wanted to *change* a field value,
     * but discovered that the field has the default value.
     *
     * @param expected The value expected by the command.
     * @param newValue The value the command wanted to set.
     * @return A new `ValueMismatch` instance.
     */
    protected fun expectedNotDefault(expected: Message, newValue: Message): ValueMismatch =
        MessageMismatch.expectedNotDefault(expected, newValue, versionNumber())

    /**
     * Creates a `ValueMismatch` for the case of discovering a value different
     * from the one expected by a command.
     *
     * @param expected The value expected by the command.
     * @param actual The value discovered instead of the expected value.
     * @param newValue The new value requested in the command.
     * @return A new `ValueMismatch` instance.
     */
    protected fun unexpectedValue(
        expected: Message,
        actual: Message,
        newValue: Message
    ): ValueMismatch =
        MessageMismatch.unexpectedValue(expected, actual, newValue, versionNumber())

    /**
     * Creates a `ValueMismatch` for the case of discovering a non-empty value,
     * when an empty string was expected by a command.
     *
     * @param actual The value discovered instead of the empty string.
     * @param newValue The new value requested in the command.
     * @return A new `ValueMismatch` instance.
     */
    protected fun expectedEmpty(actual: String, newValue: String): ValueMismatch =
        StringMismatch.expectedEmpty(actual, newValue, versionNumber())

    /**
     * Creates a `ValueMismatch` for a command that wanted to clear a string value
     * but discovered that the field is already empty.
     *
     * @param expected The value of the field that the command wanted to clear.
     * @return A new `ValueMismatch` instance.
     */
    protected fun expectedNotEmpty(expected: String): ValueMismatch =
        StringMismatch.expectedNotEmpty(expected, versionNumber())

    /**
     * Creates a `ValueMismatch` for the case of discovering a value
     * different from the one expected by a command.
     *
     * @param expected The value expected by the command.
     * @param actual The value discovered instead of the expected string.
     * @param newValue The new value requested in the command.
     * @return A new `ValueMismatch` instance.
     */
    protected fun unexpectedValue(
        expected: String,
        actual: String,
        newValue: String
    ): ValueMismatch =
        StringMismatch.unexpectedValue(expected, actual, newValue, versionNumber())

    public companion object {

        /**
         * The default number of most recent events kept in an entity's history window.
         *
         * It is the window read by the deprecated parameterless history accessors, and
         * the default number of recent events scanned per dispatch by the opt-in check
         * that rejects a signal already dispatched to the entity.
         */
        public const val DEFAULT_HISTORY_DEPTH: Int = 100
    }
}
