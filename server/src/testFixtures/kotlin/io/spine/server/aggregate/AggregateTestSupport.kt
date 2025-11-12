/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import io.spine.base.AggregateState
import io.spine.logging.WithLogging
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope

/**
 * Internal utility class for assisting in aggregate tests.
 */
object AggregateTestSupport : WithLogging {

    /**
     * Dispatches a command to an instance of an `Aggregate`.
     *
     * @param I The type of the identifiers.
     * @param A The type of the aggregate.
     * @param S The type of the aggregate state.
     * @return the list of produced event messages.
     */
    @JvmStatic
    fun <I : Any, A : Aggregate<I, S, *>, S : AggregateState<I>> dispatchCommand(
        repository: AggregateRepository<I, A, S>,
        aggregate: A,
        command: CommandEnvelope
    ): DispatchOutcome {
        val outcome = dispatchAndCollect(
            AggregateCommandEndpoint(repository, command), aggregate
        )
        warnIfErroneous(outcome)
        return outcome
    }

    /**
     * Dispatches an event to an instance of `Aggregate` into its reactor methods.
     *
     * @param I the type of aggregate identifiers.
     * @param A the type of the aggregate.
     * @param S the type of the aggregate state.
     * @return the list of produced event messages.
     */
    @JvmStatic
    fun <I : Any, A : Aggregate<I, S, *>, S : AggregateState<I>> dispatchEvent(
        repository: AggregateRepository<I, A, S>,
        aggregate: A,
        event: EventEnvelope
    ): DispatchOutcome {
        val outcome = dispatchAndCollect(
            AggregateEventReactionEndpoint(repository, event), aggregate
        )
        warnIfErroneous(outcome)
        return outcome
    }

    private fun <I : Any, A : Aggregate<I, *, *>> dispatchAndCollect(
        endpoint: AggregateEndpoint<I, A, *>,
        aggregate: A
    ): DispatchOutcome = endpoint.handleAndApplyEvents(aggregate)

    /**
     * Prints the `Error` message as a warning-level log message,
     * if the provided outcome has one.
     */
    @JvmStatic
    private fun warnIfErroneous(outcome: DispatchOutcome) {
        if (outcome.hasError()) {
            logger.atWarning().log {
                outcome.error.message
            }
        }
    }
}
