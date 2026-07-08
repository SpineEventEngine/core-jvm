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

import io.kotest.matchers.collections.shouldContain
import io.spine.core.Event
import io.spine.core.Version
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.Transaction
import io.spine.server.entity.TransactionListener
import io.spine.server.entity.TransactionTest
import io.spine.server.entity.given.tx.AggregateState
import io.spine.server.entity.given.tx.Id
import io.spine.server.entity.given.tx.TxAggregate
import io.spine.server.type.EventEnvelope
import org.junit.jupiter.api.DisplayName

private typealias AggEntity = Aggregate<Id, AggregateState, AggregateState.Builder>
private typealias AggTx = Transaction<Id, AggEntity, AggregateState, AggregateState.Builder>

@DisplayName("`AggregateTransaction` should")
internal class AggregateTransactionSpec :
    TransactionTest<Id, AggEntity, AggregateState, AggregateState.Builder>() {

    override fun createTx(entity: AggEntity): AggTx =
        AggregateTransaction(entity)

    override fun createTx(entity: AggEntity, state: AggregateState, version: Version): AggTx =
        AggregateTransaction(entity, state, version)

    override fun createTx(entity: AggEntity, listener: TransactionListener<Id>): AggTx {
        val transaction = AggregateTransaction(entity)
        transaction.setListener(listener)
        return transaction
    }

    override fun createEntity(): AggEntity = TxAggregate(id())

    override fun newState(): AggregateState =
        AggregateState.newBuilder()
            .setId(id())
            .setName("The new project name to set in tx")
            .build()

    override fun checkEventReceived(entity: AggEntity, event: Event) {
        val aggregate = entity as TxAggregate
        val actualMessage = unpack(event.message)
        aggregate.receivedEvents() shouldContain actualMessage
    }

    override fun applyEvent(tx: AggTx, event: Event): DispatchOutcome {
        val cast = tx as AggregateTransaction<*, *, *>
        val envelope = EventEnvelope.of(event)
        return cast.dispatchEvent(envelope)
    }

    // Note: the `advance version from event` case (adopting the event's own version) is not
    // applicable since the event-sourcing cutover — an `AggregateTransaction` now advances the
    // version sequentially (+1 per dispatch), exactly like a `ProcessManager`. Sequential
    // version advancement is covered by `AggregateTest` (the `AdvanceVersion` cases).
}
