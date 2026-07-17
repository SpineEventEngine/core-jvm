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

package io.spine.server.procman;

import io.spine.annotation.Internal;
import io.spine.server.delivery.CommandEndpoint;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.type.CommandEnvelope;

import static io.spine.server.command.DispatchCommand.operationFor;

/**
 * Dispatches a command to process managers.
 *
 * @param <I> the type of process manager IDs
 * @param <P> the type of process managers
 */
@SuppressWarnings("unchecked") // Operations on repository are logically checked.
@Internal
public class PmCommandEndpoint<I, P extends ProcessManager<I, ?, ?>>
        extends PmEndpoint<I, P, CommandEnvelope>
        implements CommandEndpoint<I> {

    protected PmCommandEndpoint(ProcessManagerRepository<I, P, ?> repository, CommandEnvelope cmd) {
        super(repository, cmd);
    }

    static <I, P extends ProcessManager<I, ?, ?>>
    PmCommandEndpoint<I, P> of(ProcessManagerRepository<I, P, ?> repository,
                               CommandEnvelope event) {
        return new PmCommandEndpoint<>(repository, event);
    }

    @Override
    protected void afterDispatched(I entityId) {
        repository().lifecycleOf(entityId)
                    .onDispatchCommand(envelope().command());
    }

    @Override
    protected DispatchOutcome invokeDispatcher(P processManager) {
        var lifecycle = repository().lifecycleOf(processManager.id());
        var dispatch = operationFor(lifecycle, processManager, envelope());
        PmTransaction<I, ?, ?> tx = (PmTransaction<I, ?, ?>) processManager.tx();
        return tx.perform(dispatch);
    }

    /**
     * Does nothing since a state of a process manager should not be necessarily
     * updated during the command handling.
     */
    @Override
    protected void onEmptyResult(P processManager) {
        // Do nothing.
    }
}
