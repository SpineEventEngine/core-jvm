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

import io.spine.core.Signal
import io.spine.core.SignalId
import io.spine.server.dispatch.DispatchOutcome

/**
 * A phase that dispatches an event to the entity in transaction.
 *
 * @param I The type of entity ID.
 * @param E The type of the entity.
 * @param transaction The transaction the phase is propagated in.
 * @param dispatch The event dispatch task performed by the phase.
 * @param increment The strategy for advancing the entity version.
 */
internal class EventDispatchingPhase<I : Any, E : TransactionalEntity<I, *, *>>(
    transaction: Transaction<I, *, *, *>,
    private val dispatch: EventDispatch<I, E>,
    increment: VersionIncrement
) : Phase<I>(transaction, increment) {

    override fun performDispatch(): DispatchOutcome = dispatch.perform()

    override fun entityId(): I = dispatch.entity().id()

    override fun messageId(): SignalId = dispatch.event().id()

    override fun signal(): Signal<*, *, *> = dispatch.event().outerObject()
}
