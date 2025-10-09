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
package io.spine.server.projection;

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.core.Version;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EventPlayingTransaction;
import io.spine.server.entity.VersionIncrement;
import io.spine.server.type.EventEnvelope;
import io.spine.validate.ValidatingBuilder;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A transaction, within which {@linkplain Projection projection instances} are modified.
 *
 * @param <I>
 *         the type of projection IDs
 * @param <S>
 *         the type of projection state
 * @param <B>
 *         the type of a {@code ValidatingBuilder} for the projection state
 */
@Internal
public class ProjectionTransaction<I,
                                   S extends EntityState<I>,
                                   B extends ValidatingBuilder<S>>
        extends EventPlayingTransaction<I, Projection<I, S, B>, S, B> {

    @VisibleForTesting
    ProjectionTransaction(Projection<I, S, B> projection) {
        super(projection);
    }

    @VisibleForTesting
    protected ProjectionTransaction(Projection<I, S, B> projection, S state, Version version) {
        super(projection, state, version);
    }

    /**
     * Creates a new transaction for a given {@code projection}.
     *
     * @param projection the {@code Projection} instance to start the transaction for.
     * @return the new transaction instance
     */
    protected static <I,
                      S extends EntityState<I>,
                      B extends ValidatingBuilder<S>>
    ProjectionTransaction<I, S, B> start(Projection<I, S, B> projection) {
        checkNotNull(projection);
        var tx = new ProjectionTransaction<>(projection);
        return tx;
    }

    @Override
    protected DispatchOutcome dispatch(Projection<I, S, B> projection, EventEnvelope event) {
        return projection.apply(event);
    }

    @Override
    protected VersionIncrement createVersionIncrement(EventEnvelope ignored) {
        return VersionIncrement.sequentially(this);
    }
}
