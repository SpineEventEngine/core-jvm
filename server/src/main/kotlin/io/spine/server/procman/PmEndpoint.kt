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

import io.spine.base.Error
import io.spine.core.Event
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomeHandler
import io.spine.server.entity.EntityMessageEndpoint
import io.spine.server.type.SignalEnvelope

/**
 * Common base for message endpoints of Process Managers.
 *
 * @param I The type of process manager IDs.
 * @param P The type of process managers.
 * @param M The type of message envelopes processed by the endpoint.
 */
internal abstract class PmEndpoint<I : Any,
                                   P : ProcessManager<I, *, *>,
                                   M : SignalEnvelope<*, *, *>>(
    repository: ProcessManagerRepository<I, P, *>,
    envelope: M
) : EntityMessageEndpoint<I, P, M>(repository, envelope) {

    override fun isModified(processManager: P): Boolean =
        processManager.changed() || processManager.hasUncommittedEvents()

    override fun onModified(processManager: P) {
        repository().store(processManager)
    }

    @Suppress("UNCHECKED_CAST")
    override fun repository(): ProcessManagerRepository<I, P, *> =
        super.repository() as ProcessManagerRepository<I, P, *>

    final override fun performDispatch(id: I): DispatchOutcome {
        val manager = repository().findOrCreateProcess(id)
        val outcome = runTransactionFor(manager)
        DispatchOutcomeHandler
            .from(outcome)
            .onSuccess { store(manager) }
            .onCommands { repository().postCommands(it) }
            .onEvents { repository().postEvents(it) }
            .onRejection { postRejection(it) }
            .afterSuccess { afterDispatched(id) }
            .onError { error -> dispatchingFailed(id, error) }
            .handle()
        return outcome
    }

    private fun dispatchingFailed(id: I, error: Error) {
        repository().lifecycleOf(id)
            .onDispatchingFailed(envelope(), error)
    }

    private fun postRejection(rejection: Event) {
        repository().postEvents(rejection.toSet())
    }

    /**
     * Opens a [PmTransaction] for the process manager, dispatches the message through it,
     * and commits.
     */
    protected open fun runTransactionFor(processManager: P): DispatchOutcome {
        val tx: PmTransaction<*, *, *> = repository().openTransactionFor(processManager)
        val outcome = invokeDispatcher(processManager)
        tx.commitIfActive()
        // Record the produced events as the process manager's uncommitted history right after
        // the transaction commits (success only), so that the repository journals them on
        // `store()`. A rolled-back dispatch leaves nothing to record.
        if (repository().isEventHistoryEnabled() &&
            outcome.hasSuccess() &&
            outcome.success.hasEvents()
        ) {
            processManager.recordEvents(outcome.success.producedEvents.eventList)
        }
        return outcome
    }
}
