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

package io.spine.server.projection

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.annotation.Internal
import io.spine.base.ProjectionState
import io.spine.server.delivery.EventEndpoint
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.EntityLifecycleMonitor
import io.spine.server.entity.EntityMessageEndpoint
import io.spine.server.entity.Repository
import io.spine.server.entity.TransactionListener
import io.spine.server.type.EventEnvelope

/**
 * Dispatches an event to projections.
 *
 * @param I The type of IDs of projections.
 * @param P The type of projections.
 * @param S The type of projection states.
 */
@Internal
public open class ProjectionEndpoint<I : Any,
                                     P : Projection<I, S, *>,
                                     S : ProjectionState<I>>
protected constructor(
    repository: Repository<I, P>,
    event: EventEnvelope
) : EntityMessageEndpoint<I, P, EventEnvelope>(repository, event),
    EventEndpoint<I> {

    @Suppress("UNCHECKED_CAST")
    override fun repository(): ProjectionRepository<I, P, *> =
        super.repository() as ProjectionRepository<I, P, *>

    override fun performDispatch(entityId: I): DispatchOutcome {
        val repository = repository()
        val projection = repository.findOrCreate(entityId)
        val outcome = runTransactionFor(projection)
        store(projection)
        return outcome
    }

    override fun afterDispatched(entityId: I) {
        repository().lifecycleOf(entityId)
            .onDispatchEventToSubscriber(envelope().outerObject())
    }

    protected open fun runTransactionFor(projection: P): DispatchOutcome {
        val tx = ProjectionTransaction.start(projection)
        val listener: TransactionListener<I> =
            EntityLifecycleMonitor.newInstance(repository(), projection.id)
        tx.setListener(listener)
        val outcome = invokeDispatcher(projection)
        tx.commitIfActive()
        if (outcome.hasSuccess()) {
            afterDispatched(projection.id)
        } else if (outcome.hasError()) {
            val error = outcome.error
            repository().lifecycleOf(projection.id)
                .onDispatchingFailed(envelope(), error)
        }
        return outcome
    }

    @CanIgnoreReturnValue
    override fun invokeDispatcher(projection: P): DispatchOutcome =
        projection.play(envelope().outerObject())

    override fun isModified(projection: P): Boolean = projection.changed()

    override fun onModified(projection: P) {
        repository().store(projection)
    }

    /**
     * Does nothing since a state of a projection should not necessarily
     * be updated upon execution of a [subscriber][io.spine.core.Subscribe] method.
     */
    override fun onEmptyResult(entity: P) {
        // Do nothing.
    }

    public companion object {

        /**
         * Creates a new endpoint dispatching the given event to the projections
         * of the given repository.
         */
        internal fun <I : Any, P : Projection<I, S, *>, S : ProjectionState<I>> of(
            repository: ProjectionRepository<I, P, *>,
            event: EventEnvelope
        ): ProjectionEndpoint<I, P, S> = ProjectionEndpoint(repository, event)
    }
}
