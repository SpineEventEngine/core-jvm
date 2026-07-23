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

package io.spine.server.aggregate

import io.spine.base.AggregateState
import io.spine.core.Version
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.CommandDispatchingPhase
import io.spine.server.entity.DispatchCommand
import io.spine.server.entity.EventDispatch
import io.spine.server.entity.EventDispatchingPhase
import io.spine.server.entity.Transaction
import io.spine.server.entity.VersionIncrement
import io.spine.server.type.EventEnvelope
import io.spine.validation.ValidatingBuilder

/**
 * A transaction, within which [Aggregate] instances are modified.
 *
 * Since the event-sourcing cutover, an `Aggregate` is modified exactly like a
 * [ProcessManager][io.spine.server.procman.ProcessManager]: the framework opens the
 * transaction *before* invoking the receptor, the receptor mutates
 * [builder()][Aggregate.builder] and returns its events, and the version advances
 * **by one per dispatch** via [VersionIncrement.sequentially] — no longer once per applied
 * event. The emitted events therefore carry the aggregate's pre-dispatch version, exactly as
 * process-manager events do.
 *
 * @param I The type of aggregate IDs.
 * @param S The type of aggregate state.
 * @param B The type of `ValidatingBuilder` for the aggregate state.
 */
internal class AggregateTransaction<I : Any,
                                    S : AggregateState<I>,
                                    B : ValidatingBuilder<S>> :
    Transaction<I, Aggregate<I, S, B>, S, B> {

    constructor(aggregate: Aggregate<I, S, B>) : super(aggregate)

    constructor(aggregate: Aggregate<I, S, B>, state: S, version: Version) :
        super(aggregate, state, version)

    /**
     * Executes the given command dispatch for the aggregate within this transaction.
     *
     * @param dispatch The [DispatchCommand] task.
     * @return The events generated from the command dispatch.
     */
    fun perform(dispatch: DispatchCommand<I>): DispatchOutcome {
        val vi = createVersionIncrement()
        val phase = CommandDispatchingPhase(this, dispatch, vi)
        return propagate(phase)
    }

    /**
     * Dispatches the given event to the aggregate within this transaction
     * (a `@React` reaction).
     *
     * @param event The event to dispatch.
     * @return The events generated from the reaction.
     */
    fun dispatchEvent(event: EventEnvelope): DispatchOutcome {
        val phase = EventDispatchingPhase(this, createDispatch(event), createVersionIncrement())
        return propagate(phase)
    }

    private fun createDispatch(event: EventEnvelope): EventDispatch<I, Aggregate<I, S, B>> =
        EventDispatch(this::dispatch, entity, event)

    private fun dispatch(aggregate: Aggregate<I, S, B>, event: EventEnvelope): DispatchOutcome =
        aggregate.dispatchEventInTransaction(event)

    private fun createVersionIncrement(): VersionIncrement = VersionIncrement.sequentially(this)

    companion object {

        /**
         * Creates a new transaction for the given [aggregate].
         *
         * @param aggregate The `Aggregate` instance to start the transaction for.
         * @return The new transaction instance.
         */
        fun <I : Any> start(aggregate: Aggregate<I, *, *>): AggregateTransaction<I, *, *> {
            // The transaction operates on the aggregate polymorphically; its concrete
            // state and builder types are irrelevant to opening the transaction.
            @Suppress("UNCHECKED_CAST")
            val typed =
                aggregate as Aggregate<I, AggregateState<I>, ValidatingBuilder<AggregateState<I>>>
            return AggregateTransaction(typed)
        }
    }
}
