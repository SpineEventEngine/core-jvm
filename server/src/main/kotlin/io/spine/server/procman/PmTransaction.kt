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

package io.spine.server.procman

import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.ProcessManagerState
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
 * A transaction, within which [ProcessManager] instances are modified.
 *
 * @param I The type of process manager IDs.
 * @param S The type of process manager state.
 * @param B The type of `ValidatingBuilder` for the process manager state.
 */
@Internal
public open class PmTransaction<I : Any,
                               S : ProcessManagerState<I>,
                               B : ValidatingBuilder<S>> :
    Transaction<I, ProcessManager<I, S, B>, S, B> {

    @VisibleForTesting
    public constructor(processManager: ProcessManager<I, S, B>) : super(processManager)

    @VisibleForTesting
    public constructor(processManager: ProcessManager<I, S, B>, state: S, version: Version) :
        super(processManager, state, version)

    /**
     * Executes the given command dispatch for the current entity in transaction.
     *
     * @param dispatch The [DispatchCommand] task.
     * @return The events generated from the command dispatch.
     */
    internal fun perform(dispatch: DispatchCommand<I>): DispatchOutcome {
        val vi = createVersionIncrement()
        val phase = CommandDispatchingPhase(this, dispatch, vi)
        return propagate(phase)
    }

    /**
     * Dispatches the given event to the current entity in transaction.
     *
     * @param event The event to dispatch.
     * @return The events generated from the event dispatch.
     */
    internal fun dispatchEvent(event: EventEnvelope): DispatchOutcome {
        val phase = EventDispatchingPhase(this, createDispatch(event), createVersionIncrement())
        return propagate(phase)
    }

    private fun createDispatch(event: EventEnvelope): EventDispatch<I, ProcessManager<I, S, B>> =
        EventDispatch(this::dispatch, entity, event)

    private fun dispatch(pm: ProcessManager<I, S, B>, event: EventEnvelope): DispatchOutcome =
        pm.dispatchEventInTransaction(event)

    private fun createVersionIncrement(): VersionIncrement = VersionIncrement.sequentially(this)
}
