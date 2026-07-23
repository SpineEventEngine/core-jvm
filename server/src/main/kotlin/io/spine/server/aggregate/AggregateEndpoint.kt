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

import com.google.common.collect.ImmutableList
import io.spine.core.Event
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.EntityLifecycleMonitor
import io.spine.server.entity.EntityMessageEndpoint
import io.spine.server.entity.TransactionListener
import io.spine.server.type.SignalEnvelope

/**
 * Abstract base for endpoints handling messages sent to aggregates.
 *
 * Since the event-sourcing cutover, an aggregate is dispatched exactly like a
 * [ProcessManager][io.spine.server.procman.ProcessManager]: the endpoint opens an
 * [AggregateTransaction] *before* invoking the receptor
 * ([runTransactionFor]), the receptor mutates [Aggregate.builder] and
 * returns its events, and the transaction validates the built state and commits. The produced
 * events are then journaled and the aggregate's latest state is persisted.
 *
 * @param I The type of aggregate IDs.
 * @param A The type of aggregates.
 * @param M The type of message envelopes.
 */
internal abstract class AggregateEndpoint<I : Any,
                                          A : Aggregate<I, *, *>,
                                          M : SignalEnvelope<*, *, *>>(
    repository: AggregateRepository<I, A, *>,
    envelope: M
) : EntityMessageEndpoint<I, A, M>(repository, envelope) {

    final override fun performDispatch(aggregateId: I): DispatchOutcome {
        val aggregate = loadOrCreate(aggregateId)
        val outcome = runTransactionFor(aggregate)
        if (outcome.hasSuccess()) {
            storeAndPost(aggregate, outcome)
        } else if (outcome.hasError()) {
            val error = outcome.error
            repository().lifecycleOf(aggregateId)
                .onDispatchingFailed(envelope(), error)
        }
        return outcome
    }

    /**
     * Opens an [AggregateTransaction] for the aggregate, dispatches the message through it,
     * and commits — mirroring `PmEndpoint.runTransactionFor`.
     *
     * The transaction is opened *before* the receptor runs, so the handler mutates
     * [builder()][Aggregate.builder] within it, and the aggregate version advances by one
     * per dispatch.
     */
    fun runTransactionFor(aggregate: A): DispatchOutcome {
        val tx = startTransaction(aggregate)
        val outcome = invokeDispatcher(aggregate)
        tx.commitIfActive()
        // Record the produced events as the aggregate's uncommitted history right after the
        // transaction commits (success only) — so a rolled-back dispatch leaves nothing to store.
        if (outcome.hasSuccess() && outcome.success.hasEvents()) {
            aggregate.recordEvents(outcome.success.producedEvents.eventList)
        }
        return outcome
    }

    private fun storeAndPost(aggregate: A, outcome: DispatchOutcome) {
        val success = outcome.success
        val withEvents = success.hasEvents()
        // Store when the dispatch emitted events (to journal them) or changed the business state
        // or lifecycle flags. The latter covers a zero-event, state-only reaction, which the
        // former (events-only) gate would have silently dropped.
        if (withEvents || aggregate.changed()) {
            onModified(aggregate)
        }
        if (withEvents) {
            val events = success.producedEvents.eventList
            post(events)
        } else if (success.hasRejection()) {
            post(success.rejection)
        } else {
            onEmptyResult(aggregate)
        }
        afterDispatched(aggregate.id)
    }

    private fun post(events: Collection<Event>) {
        repository().postEvents(events)
    }

    private fun post(event: Event) {
        post(ImmutableList.of(event))
    }

    private fun loadOrCreate(aggregateId: I): A = repository().loadOrCreate(aggregateId)

    private fun startTransaction(aggregate: A): AggregateTransaction<I, *, *> {
        val tx = AggregateTransaction.start(aggregate)
        val listener: TransactionListener<I> =
            EntityLifecycleMonitor.newInstance(repository(), aggregate.id)
        tx.setListener(listener)
        return tx
    }

    final override fun onModified(entity: A) {
        repository().store(entity)
    }

    final override fun isModified(aggregate: A): Boolean =
        aggregate.changed() || aggregate.hasUncommittedEvents()

    @Suppress("UNCHECKED_CAST")
    final override fun repository(): AggregateRepository<I, A, *> =
        super.repository() as AggregateRepository<I, A, *>
}
