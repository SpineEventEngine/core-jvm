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

import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomeHandler
import io.spine.server.dispatch.Success
import io.spine.server.type.CommandEnvelope

/**
 * A command dispatch operation.
 *
 * Dispatches the given [command][CommandEnvelope] to the given
 * [entity][SignalDispatchingEntity] and triggers the [EntityLifecycle].
 *
 * @param I The type of entity ID.
 */
internal class DispatchCommand<I : Any> private constructor(
    private val lifecycle: EntityLifecycle,
    private val entity: SignalDispatchingEntity<I, *, *>,
    private val command: CommandEnvelope
) {

    /**
     * Performs the operation.
     *
     * First, the command is passed to the entity for dispatching.
     *
     * Then, depending on the command handling result, either
     * [EntityLifecycle.onCommandHandled] or [EntityLifecycle.onCommandRejected] callback
     * is triggered.
     *
     * @return The produced events, including the rejections thrown by the command
     *   assignee method.
     */
    fun perform(): DispatchOutcome =
        DispatchOutcomeHandler
            .from(entity.dispatchCommandInTransaction(command))
            .onRejection { rejection -> lifecycle.onCommandRejected(command.id(), rejection) }
            .onSuccess(::onCommandHandled)
            .handle()

    private fun onCommandHandled(success: Success) {
        if (!success.hasRejection()) {
            lifecycle.onCommandHandled(command.command())
        }
    }

    fun entity(): SignalDispatchingEntity<I, *, *> = entity

    fun command(): CommandEnvelope = command

    companion object {

        /**
         * Creates a new command dispatch operation.
         *
         * @param lifecycle The lifecycle of the entity receiving the command.
         * @param entity The entity to dispatch the command to.
         * @param command The command to dispatch.
         */
        fun <I : Any> operationFor(
            lifecycle: EntityLifecycle,
            entity: SignalDispatchingEntity<I, *, *>,
            command: CommandEnvelope
        ): DispatchCommand<I> = DispatchCommand(lifecycle, entity, command)
    }
}
