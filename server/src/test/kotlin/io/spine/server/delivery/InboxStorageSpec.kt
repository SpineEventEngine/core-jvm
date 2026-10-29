/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.delivery

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.server.delivery.given.GatedStorageFactory
import io.spine.server.delivery.given.TestInboxMessages.catchingUp
import io.spine.server.delivery.given.TestInboxMessages.delivered
import io.spine.server.delivery.given.TestInboxMessages.toDeliver
import io.spine.server.entity.given.concurrency.Gate
import io.spine.server.entity.given.concurrency.Worker
import io.spine.test.delivery.Calc
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests the reads of an [InboxStorage], and its concurrent use over
 * [record storages][GatedStorageFactory] that can hold a thread at a [Gate], the way a real
 * storage holds it during its I/O.
 */
@DisplayName("`InboxStorage` should")
internal class InboxStorageSpec {

    private val factory = GatedStorageFactory()
    private val storage = InboxStorage(factory, false)

    /**
     * The message on which a thread is held inside the record storage.
     */
    private val held = messageTo("a-calc")

    @Test
    fun `read the messages held for the catch-up in the given shard`() {
        val type = TypeUrl.of(Calc::class.java)
        val shard = DeliveryStrategy.newIndex(0, 2)
        val anotherShard = DeliveryStrategy.newIndex(1, 2)
        val catchingUp = catchingUp("a-calc", type).inShard(shard)
        val catchingUpToo = catchingUp("another-calc", type).inShard(shard)
        storage.writeBatch(
            listOf(
                catchingUp,
                toDeliver("a-calc", type).inShard(shard),
                catchingUpToo,
                delivered("a-calc", type).inShard(shard),
                catchingUp("a-calc", type).inShard(anotherShard)
            )
        )

        storage.readToCatchUp(shard).asSequence().toList() shouldContainExactlyInAnyOrder
                listOf(catchingUp, catchingUpToo)
    }

    /**
     * A `Delivery` has one `InboxStorage` for all the shards, so the storage I/O for
     * one message must not delay the writing and the removal of the other ones.
     */
    @Nested inner class
    `serve other messages` {

        @Test
        fun `while a message is being written`() {
            assertServesDuring { storage.write(held) }

            storage.read(held.id).shouldBePresent()
        }

        @Test
        fun `while a batch is being written`() {
            assertServesDuring { storage.writeBatch(listOf(messageTo("one-more-calc"), held)) }

            storage.read(held.id).shouldBePresent()
        }

        @Test
        fun `while a batch is being removed`() {
            storage.write(held)

            assertServesDuring { storage.removeBatch(listOf(held)) }

            storage.read(held.id).shouldBeEmpty()
        }

        /**
         * Holds the given operation inside the record storage, and checks that
         * another thread writes and removes other messages meanwhile.
         *
         * Returns after the held operation is let go and has completed.
         *
         * @param operation The operation to hold. It must write or remove the [held] message.
         */
        private fun assertServesDuring(operation: () -> Unit) {
            val gate = Gate()
            factory.hold(held.id, gate)
            val holder = Worker(operation)
            try {
                gate.awaitReached(by = holder)

                val written = messageTo("another-calc")
                val batched = messageTo("yet-another-calc")
                Worker {
                    storage.write(written)
                    storage.writeBatch(listOf(batched))
                    storage.removeBatch(listOf(written))
                }.result()

                withClue("The held operation completed before the gate was opened.") {
                    holder.isDone shouldBe false
                }
                storage.read(written.id).shouldBeEmpty()
                storage.read(batched.id).shouldBePresent()
            } finally {
                gate.open()
            }
            holder.result()
        }
    }
}

/**
 * Creates a message to deliver to the calculator with the given ID.
 *
 * Every call generates a new message ID, even for the same calculator.
 */
private fun messageTo(calculator: String): InboxMessage =
    toDeliver(calculator, TypeUrl.of(Calc::class.java))

/**
 * Returns a copy of this message with a new ID, which places it in the given shard.
 */
private fun InboxMessage.inShard(shard: ShardIndex): InboxMessage =
    copy { id = InboxMessageMixin.generateIdWith(shard) }
