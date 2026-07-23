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

package io.spine.server.aggregate

import io.spine.server.delivery.CommandEndpoint
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.DispatchCommand.Companion.operationFor
import io.spine.server.type.CommandEnvelope
import io.spine.util.Exceptions.newIllegalStateException

/**
 * Dispatches commands to aggregates of the associated `AggregateRepository`.
 *
 * @param I The type of the aggregate IDs.
 * @param A The type of the aggregates managed by the parent repository.
 */
internal class AggregateCommandEndpoint<I : Any, A : Aggregate<I, *, *>>(
    repo: AggregateRepository<I, A, *>,
    command: CommandEnvelope
) : AggregateEndpoint<I, A, CommandEnvelope>(repo, command),
    CommandEndpoint<I> {

    @Suppress("UNCHECKED_CAST") // The transaction wraps this aggregate; the cast is safe.
    override fun invokeDispatcher(aggregate: A): DispatchOutcome {
        val lifecycle = repository().lifecycleOf(aggregate.id)
        val dispatch = operationFor(lifecycle, aggregate, envelope())
        val tx = aggregate.activeTransaction() as AggregateTransaction<I, *, *>
        return tx.perform(dispatch)
    }

    override fun afterDispatched(entityId: I) {
        repository().lifecycleOf(entityId)
            .onDispatchCommand(envelope().command())
    }

    /**
     * Throws [IllegalStateException] with the message containing details of the aggregate
     * and the command in response to which the aggregate generated an empty set of
     * event messages.
     *
     * @throws IllegalStateException Always.
     */
    override fun onEmptyResult(aggregate: A) {
        val cmd = envelope()
        val entityId = aggregate.idAsString()
        val entityClass = aggregate.javaClass.name
        val commandId = cmd.id().value()
        val commandClass = cmd.messageClass()
        throw newIllegalStateException(
            "The aggregate (class: %s, ID: %s) produced empty response for " +
                    "the command (class: %s, ID: %s).",
            entityClass,
            entityId,
            commandClass,
            commandId
        )
    }
}
