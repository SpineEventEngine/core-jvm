/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.core.Event;
import io.spine.core.EventId;
import io.spine.server.dispatch.BatchDispatchOutcome;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityLifecycleMonitor;
import io.spine.server.entity.EntityMessageEndpoint;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.TransactionListener;
import io.spine.server.type.SignalEnvelope;

import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Functions.identity;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * Abstract base for endpoints handling messages sent to aggregates.
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
        var flagsBefore = aggregate.lifecycleFlags();
        var outcome = handleAndApplyEvents(aggregate);
        if (outcome.hasSuccess()) {
            storeAndPost(aggregate, outcome, flagsBefore);
        } else if (outcome.hasError()) {
            var error = outcome.getError();
            repository().lifecycleOf(aggregateId)
                        .onDispatchingFailed(envelope(), error);
        }
        return outcome;
    }

    private void storeAndPost(A aggregate, DispatchOutcome outcome, LifecycleFlags flagsBefore) {
        var success = outcome.getSuccess();
        var flagsAfter = aggregate.lifecycleFlags();
        var withEvents = success.hasEvents();
        if (withEvents ||
                (flagsAfter != null && !flagsBefore.equals(flagsAfter))) {
            store(aggregate);
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

    @CanIgnoreReturnValue
    final DispatchOutcome handleAndApplyEvents(A aggregate) {
        var outcome = invokeDispatcher(aggregate);
        var successfulOutcome = outcome.getSuccess();
        return successfulOutcome.hasEvents()
               ? applyProducedEvents(aggregate, outcome)
               : outcome;
    }

    /**
     * Applies the produced by the Aggregate events to the Aggregate.
     *
     * @param aggregate
     *         the target Aggregate
     * @param commandOutcome
     *         the successful command propagation outcome
     * @return the outcome of the command propagation with the events with the correct versions or
     *         the erroneous outcome of applying an event
     */
    private DispatchOutcome applyProducedEvents(A aggregate, DispatchOutcome commandOutcome) {
        var events = commandOutcome.getSuccess()
                                   .getProducedEvents()
                                   .getEventList();
        AggregateTransaction<I, ?, ?> tx = startTransaction(aggregate);
        var snapshotTrigger = repository().snapshotTrigger();
        var batchDispatchOutcome = aggregate.apply(events, snapshotTrigger);
        if (batchDispatchOutcome.getSuccessful()) {
            tx.commitIfActive();
            return correctProducedEvents(commandOutcome, batchDispatchOutcome);
        } else {
            return firstErroneousOutcome(batchDispatchOutcome);
        }
    }

    /**
     * Corrects the versions of the produced events in the command outcome.
     *
     * @param commandOutcome
     *         the successful command outcome
     * @param eventDispatch
     *         the result of applying the event
     * @return the same command outcome but with the events of the correct versions
     * @implNote After the versions are corrected, the resulting message isn't validated
     *         again, as it comes in a valid state and the versions are all fine.
     *         Triggering the extra validation is CPU-costly here.
     */
    private static DispatchOutcome
    correctProducedEvents(DispatchOutcome commandOutcome, BatchDispatchOutcome eventDispatch) {
        var correctedCommandOutcome = commandOutcome.toBuilder();
        var eventsBuilder = correctedCommandOutcome.getSuccessBuilder()
                                                   .getProducedEventsBuilder();
        Map<EventId, Event.Builder> correctedEvents = eventsBuilder
                .getEventBuilderList()
                .stream()
                .collect(toImmutableMap(Event.Builder::getId, identity()));
        for (var outcome : eventDispatch.getOutcomeList()) {
            var signalId = outcome.getPropagatedSignal()
                                  .getId();
            var eventId = unpack(signalId, EventId.class);
            var event = checkNotNull(correctedEvents.get(eventId));
            var signalVersion = outcome.getPropagatedSignal().getVersion();
            event.getContextBuilder()
                 .setVersion(signalVersion);
        }
        return correctedCommandOutcome.build();
    }

    /**
     * Finds the first erroneous outcome in the given batchDispatchOutcome report.
     *
     * @param outcome
     *         the non-successful dispatch
     * @return the first found outcome with an error
     */
    private static DispatchOutcome firstErroneousOutcome(BatchDispatchOutcome outcome) {
        var erroneous = outcome.getOutcomeList()
                .stream()
                .filter(DispatchOutcome::hasError)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Dispatch was marked failed but no error occurred."
                ));
        return erroneous;
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
        return aggregate.hasUncommittedEvents();
    }

    @Override
    public final AggregateRepository<I, A, ?> repository() {
        return (AggregateRepository<I, A, ?>) super.repository();
    }
}
