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

package io.spine.server.procman;

import io.spine.base.Error;
import io.spine.core.Event;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.entity.EntityMessageEndpoint;
import io.spine.server.type.SignalEnvelope;

/**
 * Common base message for endpoints of Process Managers.
 *
 * @param <I>
 *         the type of process manager IDs
 * @param <P>
 *         the type of process managers
 * @param <M>
 *         the type of message envelopes processed by the endpoint
 */
abstract class PmEndpoint<I,
                          P extends ProcessManager<I, ?, ?>,
                          M extends SignalEnvelope<?, ?, ?>>
        extends EntityMessageEndpoint<I, P, M> {

    PmEndpoint(ProcessManagerRepository<I, P, ?> repository, M envelope) {
        super(repository, envelope);
    }

    @Override
    protected boolean isModified(P processManager) {
        var result = processManager.changed();
        return result;
    }

    @Override
    protected void onModified(P processManager) {
        repository().store(processManager);
    }

    @Override
    public ProcessManagerRepository<I, P, ?> repository() {
        return (ProcessManagerRepository<I, P, ?>) super.repository();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("UnnecessaryInheritDoc") // IDEA bug.
    @Override
    protected final DispatchOutcome performDispatch(I id) {
        var manager = repository().findOrCreate(id);
        var outcome = runTransactionFor(manager);
        DispatchOutcomeHandler
                .from(outcome)
                .onSuccess(success -> store(manager))
                .onCommands(repository()::postCommands)
                .onEvents(repository()::postEvents)
                .onRejection(this::postRejection)
                .afterSuccess(success -> afterDispatched(id))
                .onError(error -> dispatchingFailed(id, error))
                .handle();
        return outcome;
    }

    private void dispatchingFailed(I id, Error error) {
        repository().lifecycleOf(id)
                    .onDispatchingFailed(envelope(), error);
    }

    private void postRejection(Event rejection) {
        repository().postEvents(rejection.toSet());
    }

    protected DispatchOutcome runTransactionFor(P processManager) {
        PmTransaction<?, ?, ?> tx = repository().beginTransactionFor(processManager);
        var outcome = invokeDispatcher(processManager);
        tx.commitIfActive();
        return outcome;
    }
}
