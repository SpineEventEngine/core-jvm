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

package io.spine.server.command;

import io.spine.annotation.Internal;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.dispatch.Success;
import io.spine.server.entity.EntityLifecycle;
import io.spine.server.type.CommandEnvelope;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A command dispatch operation.
 *
 * <p>Dispatches the given {@linkplain CommandEnvelope command} to the given
 * {@linkplain AssigneeEntity entity} and triggers the {@link EntityLifecycle}.
 *
 * @param <I>
 *         the type of entity ID
 */
@Internal
public final class DispatchCommand<I> {

    private final EntityLifecycle lifecycle;
    private final AssigneeEntity<I, ?, ?> entity;
    private final CommandEnvelope command;

    private DispatchCommand(EntityLifecycle lifecycle,
                            AssigneeEntity<I, ?, ?> entity,
                            CommandEnvelope command) {
        this.lifecycle = lifecycle;
        this.entity = entity;
        this.command = command;
    }

    public static <I> DispatchCommand<I> operationFor(EntityLifecycle lifecycle,
                                                      AssigneeEntity<I, ?, ?> entity,
                                                      CommandEnvelope command) {
        checkNotNull(lifecycle);
        checkNotNull(entity);
        checkNotNull(command);

        return new DispatchCommand<>(lifecycle, entity, command);
    }

    /**
     * Performs the operation.
     *
     * <p>First, the command is {@linkplain AssigneeEntity#dispatchCommand(CommandEnvelope)
     * passed} to the entity.
     *
     * <p>Then, depending on the command handling result, either
     * {@link EntityLifecycle#onCommandHandled EntityLifecycle.onCommandHandled(...)} or
     * {@link EntityLifecycle#onCommandRejected EntityLifecycle.onCommandRejected(...)} callback
     * is triggered.
     *
     * @return the produced events including the rejections thrown by the command assignee method
     */
    public DispatchOutcome perform() {
        return DispatchOutcomeHandler
                .from(entity.dispatchCommand(command))
                .onRejection(rejection -> lifecycle.onCommandRejected(command.id(), rejection))
                .onSuccess(this::onCommandHandler)
                .handle();
    }

    private void onCommandHandler(Success success) {
        if (!success.hasRejection()) {
            lifecycle.onCommandHandled(command.command());
        }
    }

    public AssigneeEntity<I, ?, ?> entity() {
        return entity;
    }

    public CommandEnvelope command() {
        return command;
    }
}
