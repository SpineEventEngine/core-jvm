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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.core.Event
import io.spine.core.Versions.increment
import io.spine.core.Versions.zero
import io.spine.server.dispatch.BatchDispatchOutcome
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.Success
import io.spine.server.test.shared.StringEntity
import io.spine.server.type.EventEnvelope
import io.spine.server.type.given.GivenEvent.arbitrary
import io.spine.server.type.given.GivenEvent.withVersion
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`TransactionalEventPlayer` should")
internal class TransactionalEventPlayerSpec {

    @Test
    fun `require active transaction to play events`() {
        shouldThrow<IllegalStateException> {
            TxPlayingEntity().play(arbitrary())
        }
    }

    @Test
    fun `delegate applying events to transaction when playing`() {
        val entity = TxPlayingEntity()
        // Constructing the transaction injects it into the entity.
        val tx = RecordingTransaction(entity, active = true, stateChanged = false)
        val v1 = increment(zero())
        val firstEvent = withVersion(v1)
        val secondEvent = withVersion(increment(v1))

        entity.play(listOf(firstEvent, secondEvent))

        tx.dispatched(firstEvent) shouldBe true
        tx.dispatched(secondEvent) shouldBe true
    }

    /**
     * Stub implementation of [TransactionalEntity].
     */
    private class TxPlayingEntity :
        TransactionalEntity<String, StringEntity, StringEntity.Builder>("`TxPlayingEntity` ID"),
        EventPlayer {

        override fun play(events: Iterable<Event>): BatchDispatchOutcome =
            EventPlayer.forTransactionOf(this).play(events)
    }

    /**
     * Stub implementation of [Transaction] that records the events dispatched to it.
     */
    private class RecordingTransaction(
        entity: TxPlayingEntity,
        active: Boolean,
        stateChanged: Boolean
    ) : EventPlayingTransaction<String, TxPlayingEntity, StringEntity, StringEntity.Builder>(
        entity
    ) {

        private val dispatchedEvents = mutableListOf<Event>()

        init {
            if (!active) {
                deactivate()
            }
            if (stateChanged) {
                markStateChanged()
            }
        }

        override fun dispatch(entity: TxPlayingEntity, event: EventEnvelope): DispatchOutcome {
            val outer = event.outerObject()
            dispatchedEvents.add(outer)
            return DispatchOutcome.newBuilder()
                .setPropagatedSignal(outer.messageId())
                .setSuccess(Success.getDefaultInstance())
                .build()
        }

        override fun createVersionIncrement(event: EventEnvelope): VersionIncrement =
            VersionIncrement.fromEvent(event)

        fun dispatched(event: Event): Boolean = dispatchedEvents.contains(event)
    }
}
