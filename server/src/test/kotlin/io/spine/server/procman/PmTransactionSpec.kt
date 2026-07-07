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

import io.kotest.matchers.collections.shouldContain
import io.spine.core.Event
import io.spine.core.Version
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.Transaction
import io.spine.server.entity.TransactionListener
import io.spine.server.entity.TransactionTest
import io.spine.server.entity.given.tx.Id
import io.spine.server.entity.given.tx.PmState
import io.spine.server.entity.given.tx.TxProcessManager
import io.spine.server.type.EventEnvelope
import org.junit.jupiter.api.DisplayName

private typealias PmEntity = ProcessManager<Id, PmState, PmState.Builder>
private typealias PmTx = Transaction<Id, PmEntity, PmState, PmState.Builder>

@DisplayName("`PmTransaction` should")
internal class PmTransactionSpec :
    TransactionTest<Id, PmEntity, PmState, PmState.Builder>() {

    override fun createTx(entity: PmEntity): PmTx =
        PmTransaction(entity)

    override fun createTx(entity: PmEntity, state: PmState, version: Version): PmTx =
        PmTransaction(entity, state, version)

    override fun createTx(entity: PmEntity, listener: TransactionListener<Id>): PmTx {
        val transaction = PmTransaction(entity)
        transaction.setListener(listener)
        return transaction
    }

    override fun createEntity(): PmEntity = TxProcessManager(id())

    override fun newState(): PmState =
        PmState.newBuilder()
            .setId(id())
            .setName("The new project name for procman tx tests")
            .build()

    override fun checkEventReceived(entity: PmEntity, event: Event) {
        val processManager = entity as TxProcessManager
        val actualMessage = unpack(event.message)
        processManager.receivedEvents() shouldContain actualMessage
    }

    override fun applyEvent(tx: PmTx, event: Event): DispatchOutcome {
        val cast = tx as PmTransaction<*, *, *>
        val envelope = EventEnvelope.of(event)
        return cast.dispatchEvent(envelope)
    }
}
