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

import io.spine.server.delivery.CommandEndpoint
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.DispatchCommand.operationFor
import io.spine.server.type.CommandEnvelope

/**
 * Dispatches a command to process managers.
 *
 * @param I The type of process manager IDs.
 * @param P The type of process managers.
 */
internal open class PmCommandEndpoint<I : Any, P : ProcessManager<I, *, *>>
protected constructor(
    repository: ProcessManagerRepository<I, P, *>,
    cmd: CommandEnvelope
) : PmEndpoint<I, P, CommandEnvelope>(repository, cmd),
    CommandEndpoint<I> {

    override fun afterDispatched(entityId: I) {
        repository().lifecycleOf(entityId)
            .onDispatchCommand(envelope().command())
    }

    @Suppress("UNCHECKED_CAST") // The transaction wraps this process manager; the cast is safe.
    override fun invokeDispatcher(processManager: P): DispatchOutcome {
        val lifecycle = repository().lifecycleOf(processManager.id)
        val dispatch = operationFor(lifecycle, processManager, envelope())
        val tx = processManager.activeTransaction() as PmTransaction<I, *, *>
        return tx.perform(dispatch)
    }

    /**
     * Does nothing since a state of a process manager should not be necessarily
     * updated during the command handling.
     */
    override fun onEmptyResult(processManager: P) {
        // Do nothing.
    }

    companion object {

        /**
         * Creates a new endpoint dispatching the given command to the process managers
         * of the given repository.
         */
        fun <I : Any, P : ProcessManager<I, *, *>> of(
            repository: ProcessManagerRepository<I, P, *>,
            cmd: CommandEnvelope
        ): PmCommandEndpoint<I, P> = PmCommandEndpoint(repository, cmd)
    }
}
