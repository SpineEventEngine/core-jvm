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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import io.spine.core.Event;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityLifecycleMonitor;
import io.spine.server.entity.EntityMessageEndpoint;
import io.spine.server.entity.TransactionListener;
import io.spine.server.type.SignalEnvelope;

import java.util.Collection;

/**
 * Abstract base for endpoints handling messages sent to aggregates.
 *
 * <p>Since the event-sourcing cutover, an aggregate is dispatched exactly like a
 * {@link io.spine.server.procman.ProcessManager ProcessManager}: the endpoint opens an
 * {@link AggregateTransaction} <em>before</em> invoking the receptor
 * ({@link #runTransactionFor(Aggregate)}), the receptor mutates {@link Aggregate#builder()} and
 * returns its events, and the transaction validates the built state and commits. The produced
 * events are then journaled and the aggregate's latest state is persisted.
 *
 * @param <I>
 *         the type of aggregate IDs
 * @param <A>
 *         the type of aggregates
 * @param <M>
 *         the type of message envelopes
 */
abstract class AggregateEndpoint<I,
                                 A extends Aggregate<I, ?, ?>,
                                 M extends SignalEnvelope<?, ?, ?>>
        extends EntityMessageEndpoint<I, A, M> {

    AggregateEndpoint(AggregateRepository<I, A, ?> repository, M envelope) {
        super(repository, envelope);
    }

    @Override
    protected final DispatchOutcome performDispatch(I aggregateId) {
        var aggregate = loadOrCreate(aggregateId);
        var outcome = runTransactionFor(aggregate);
        if (outcome.hasSuccess()) {
            storeAndPost(aggregate, outcome);
        } else if (outcome.hasError()) {
            var error = outcome.getError();
            repository().lifecycleOf(aggregateId)
                        .onDispatchingFailed(envelope(), error);
        }
        return outcome;
    }

    /**
     * Opens an {@link AggregateTransaction} for the aggregate, dispatches the message through it,
     * and commits — mirroring {@code PmEndpoint.runTransactionFor}.
     *
     * <p>The transaction is opened <em>before</em> the receptor runs, so the handler mutates
     * {@link Aggregate#builder() builder()} within it, and the aggregate version advances by one
     * per dispatch.
     */
    final DispatchOutcome runTransactionFor(A aggregate) {
        var tx = startTransaction(aggregate);
        var outcome = invokeDispatcher(aggregate);
        tx.commitIfActive();
        // Record the produced events as the aggregate's uncommitted history right after the
        // transaction commits (success only) — so a rolled-back dispatch leaves nothing to store.
        if (outcome.hasSuccess() && outcome.getSuccess().hasEvents()) {
            aggregate.recordEvents(outcome.getSuccess()
                                          .getProducedEvents()
                                          .getEventList());
        }
        return outcome;
    }

    private void storeAndPost(A aggregate, DispatchOutcome outcome) {
        var success = outcome.getSuccess();
        var withEvents = success.hasEvents();
        // Store when the dispatch emitted events (to journal them) or changed the business state
        // or lifecycle flags. The latter covers a zero-event, state-only reaction, which the
        // former (events-only) gate would have silently dropped.
        if (withEvents || aggregate.changed()) {
            onModified(aggregate);
        }
        if (withEvents) {
            var events = success.getProducedEvents().getEventList();
            post(events);
        } else if (success.hasRejection()) {
            post(success.getRejection());
        } else {
            onEmptyResult(aggregate);
        }
        afterDispatched(aggregate.id());
    }

    private void post(Collection<Event> events) {
        repository().postEvents(events);
    }

    private void post(Event event) {
        post(ImmutableList.of(event));
    }

    private A loadOrCreate(I aggregateId) {
        return repository().loadOrCreate(aggregateId);
    }

    private AggregateTransaction<I, ?, ?> startTransaction(A aggregate) {
        AggregateTransaction<I, ?, ?> tx = AggregateTransaction.start(aggregate);
        TransactionListener<I> listener =
                EntityLifecycleMonitor.newInstance(repository(), aggregate.id());
        tx.setListener(listener);
        return tx;
    }

    @Override
    protected final void onModified(A entity) {
        repository().store(entity);
    }

    @Override
    protected final boolean isModified(A aggregate) {
        return aggregate.changed() || aggregate.hasUncommittedEvents();
    }

    @Override
    public final AggregateRepository<I, A, ?> repository() {
        return (AggregateRepository<I, A, ?>) super.repository();
    }
}
