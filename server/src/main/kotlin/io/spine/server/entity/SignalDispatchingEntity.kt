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

import io.spine.annotation.Internal
import io.spine.base.EntityState
import io.spine.server.command.AssigneeEntity
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.type.EventEnvelope
import io.spine.validation.ValidatingBuilder

/**
 * Abstract base for entities that dispatch signals — both commands and events —
 * to their receptors.
 *
 * Extends [AssigneeEntity], which already dispatches commands to their `@Assign` receptors,
 * with the ability to dispatch events to the receptors reacting to them.
 *
 * This is the entity-level counterpart of [SignalDispatchingRepository]: the repository routes
 * a signal to the target entity, and the entity dispatches it to the matching receptor.
 *
 * Because such an entity emits events, it keeps the [recent history][recentEventHistory] of
 * them, served lazily from the entity's durable journal through the loader the repository
 * [installs][setEventHistoryLoader].
 *
 * @param I The type of the entity identifiers.
 * @param S The type of the entity state.
 * @param B The type of the builders for the entity state.
 *
 * @see AssigneeEntity
 * @see SignalDispatchingRepository
 */
public abstract class SignalDispatchingEntity<I : Any,
                                              S : EntityState<I>,
                                              B : ValidatingBuilder<S>> :
    AssigneeEntity<I, S, B> {

    private val recentEventHistory = RecentEventHistory()

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
    @Internal
    public fun setEventHistoryLoader(loader: EventHistoryLoader) {
        recentEventHistory.useLoader(loader)
    }

    /**
     * Dispatches the given event to the receptor reacting to it.
     *
     * Reacting to an event may result in emitting one or more event messages, described by
     * the returned outcome.
     *
     * @param event The envelope with the event to dispatch.
     * @return the outcome of dispatching the event.
     */
    protected abstract fun dispatchEvent(event: EventEnvelope): DispatchOutcome
}
