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

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.core.Version;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EventPlayingTransaction;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.VersionIncrement;
import io.spine.server.type.EventEnvelope;
import io.spine.validate.ValidatingBuilder;

/**
 * A transaction, within which {@linkplain Aggregate Aggregate instances} are modified.
 *
 * @param <I> the type of aggregate IDs
 * @param <S> the type of aggregate state
 * @param <B> the type of {@code ValidatingBuilder} for the aggregate state
 */
@Internal
public class AggregateTransaction<I,
                                  S extends EntityState<I>,
                                  B extends ValidatingBuilder<S>>
        extends EventPlayingTransaction<I, Aggregate<I, S, B>, S, B> {

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

    @Override
    protected final DispatchOutcome dispatch(Aggregate<I, S, B> aggregate, EventEnvelope event) {
        return aggregate.invokeApplier(event);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Additionally, notifies the {@code Aggregate} instance that the event has been played.
     */
    @Override
    public DispatchOutcome play(EventEnvelope event) {
        var outcome = super.play(event);
        entity().onAfterEventPlayed(event);
        return outcome;
    }

    @Override
    protected VersionIncrement createVersionIncrement(EventEnvelope event) {
        return VersionIncrement.fromEvent(event);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose to the package.
     */
    @Override
    protected final LifecycleFlags lifecycleFlags() {
        return super.lifecycleFlags();
    }

    /**
     * Returns the current version of the "dirty" entity being modified in scope of the transaction.
     */
    final Version currentVersion() {
        return version();
    }
}
