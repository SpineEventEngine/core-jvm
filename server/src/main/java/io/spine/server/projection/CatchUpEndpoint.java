/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.projection;

import io.spine.base.ProjectionState;
import io.spine.server.delivery.event.CatchUpStarted;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.Repository;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeName;

import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;

/**
 * Dispatches an event to projections during the catch-up.
 *
 * <p>Handles the special {@link CatchUpStarted} event by deleting the state of the target
 * projection instance.
 *
 * @param <I>
 *         the type of IDs of projections
 * @param <P>
 *         the type of projections
 * @param <S>
 *         the type of projection states
 */
final class CatchUpEndpoint<I, P extends Projection<I, S, ?>, S extends ProjectionState<I>>
        extends ProjectionEndpoint<I, P, S> {

    private static final TypeName CATCH_UP_STARTED =
            TypeName.from(CatchUpStarted.getDescriptor());

    private CatchUpEndpoint(Repository<I, P> repository, EventEnvelope event) {
        super(repository, event);
    }

    static <I, P extends Projection<I, S, ?>, S extends ProjectionState<I>>
    CatchUpEndpoint<I, P, S> of(ProjectionRepository<I, P, ?> repository, EventEnvelope event) {
        return new CatchUpEndpoint<>(repository, event);
    }

    /**
     * Does nothing, as no lifecycle events should be emitted during the catch-up.
     */
    @Override
    protected void afterDispatched(I entityId) {
        // do nothing.
    }

    @Override
    protected DispatchOutcome performDispatch(I entityId) {
        var actualTypeName = envelope().messageTypeName();
        if (CATCH_UP_STARTED.equals(actualTypeName)) {
            onCatchUpStarted(entityId);
            return successfulOutcome(envelope().messageId());
        } else {
            return super.performDispatch(entityId);
        }
    }

    private void onCatchUpStarted(I entityId) {
        var repository = repository();
        var event = (CatchUpStarted) envelope().message();
        repository.recordStorage()
                  .delete(entityId);
        repository.lifecycleOf(entityId)
                  .onEntityPreparedForCatchUp(event.getId());
    }
}
