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

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.AggregateState;
import io.spine.core.Version;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.CommandDispatchingPhase;
import io.spine.server.entity.DispatchCommand;
import io.spine.server.entity.EventDispatch;
import io.spine.server.entity.EventDispatchingPhase;
import io.spine.server.entity.Phase;
import io.spine.server.entity.Transaction;
import io.spine.server.entity.VersionIncrement;
import io.spine.server.type.EventEnvelope;
import io.spine.validation.ValidatingBuilder;

/**
 * A transaction, within which {@linkplain Aggregate Aggregate instances} are modified.
 *
 * <p>Since the event-sourcing cutover, an {@code Aggregate} is modified exactly like a
 * {@link io.spine.server.procman.ProcessManager ProcessManager}: the framework opens the
 * transaction <em>before</em> invoking the receptor, the receptor mutates
 * {@link Aggregate#builder() builder()} and returns its events, and the version advances
 * <b>by one per dispatch</b> via
 * {@link VersionIncrement#sequentially(Transaction)} — no longer once per applied event. The
 * emitted events therefore carry the aggregate's pre-dispatch version, exactly as
 * process-manager events do.
 *
 * @param <I> the type of aggregate IDs
 * @param <S> the type of aggregate state
 * @param <B> the type of {@code ValidatingBuilder} for the aggregate state
 */
@Internal
public class AggregateTransaction<I,
                                  S extends AggregateState<I>,
                                  B extends ValidatingBuilder<S>>
        extends Transaction<I, Aggregate<I, S, B>, S, B> {

    @VisibleForTesting
    AggregateTransaction(Aggregate<I, S, B> aggregate) {
        super(aggregate);
    }

    @VisibleForTesting
    protected AggregateTransaction(Aggregate<I, S, B> aggregate, S state, Version version) {
        super(aggregate, state, version);
    }

    /**
     * Creates a new transaction for a given {@code aggregate}.
     *
     * @param aggregate the {@code Aggregate} instance to start the transaction for.
     * @return the new transaction instance
     */
    static <I> AggregateTransaction<I, ?, ?> start(Aggregate<I, ?, ?> aggregate) {
        @SuppressWarnings("RedundantExplicitVariableType")  /* To enable wildcard instantiation. */
        AggregateTransaction<I, ?, ?> tx = new AggregateTransaction<>(aggregate);
        return tx;
    }

    /**
     * Executes the given command dispatch for the aggregate within this transaction.
     *
     * @param dispatch the {@code DispatchCommand} task
     * @return the events generated from the command dispatch
     * @see Aggregate#dispatchCommand(io.spine.server.type.CommandEnvelope)
     */
    final DispatchOutcome perform(DispatchCommand<I> dispatch) {
        var vi = createVersionIncrement();
        Phase<I> phase = new CommandDispatchingPhase<>(this, dispatch, vi);
        return propagate(phase);
    }

    /**
     * Dispatches the given event to the aggregate within this transaction (an {@code @React}
     * reaction).
     *
     * @param event the event to dispatch
     * @return the events generated from the reaction
     * @see Aggregate#dispatchEvent(EventEnvelope)
     */
    final DispatchOutcome dispatchEvent(EventEnvelope event) {
        Phase<I> phase = new EventDispatchingPhase<>(
                this,
                createDispatch(event),
                createVersionIncrement()
        );
        return propagate(phase);
    }

    private EventDispatch<I, Aggregate<I, S, B>> createDispatch(EventEnvelope event) {
        return new EventDispatch<>(this::dispatch, entity(), event);
    }

    private DispatchOutcome dispatch(Aggregate<I, S, B> aggregate, EventEnvelope event) {
        return aggregate.dispatchEvent(event);
    }

    private VersionIncrement createVersionIncrement() {
        return VersionIncrement.sequentially(this);
    }
}
