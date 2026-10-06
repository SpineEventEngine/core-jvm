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

import com.google.protobuf.Message
import io.kotest.assertions.withClue
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.server.ContextSpec
import io.spine.server.delivery.given.TestInboxMessages.toDeliver
import io.spine.server.delivery.given.concurrency.Gate
import io.spine.server.delivery.given.concurrency.Worker
import io.spine.server.storage.DelegatingRecordStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.RecordWithColumns
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.delivery.Calc
import io.spine.type.TypeUrl
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests the concurrent use of an [InboxStorage] against a [record storage][GatedRecords]
 * which can hold a thread at a [Gate], the way a real storage holds it during its I/O.
 */
@DisplayName("`InboxStorage` should")
internal class InboxStorageSpec {

    private val factory = GatedStorageFactory()
    private val storage = InboxStorage(factory, false)

    /**
     * The message on which a thread is held inside the record storage.
     */
    private val held = messageTo("a-calc")

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
 * Creates in-memory record storages that can hold a thread at a [Gate].
 *
 * A thread writing or deleting a record [selected][hold] by a test stops inside
 * the record storage — where a real storage does its I/O — until the gate opens.
 */
private class GatedStorageFactory : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()
    private val gates = ConcurrentHashMap<Any, Gate>()

    /**
     * Makes the record storages hold, at the given gate, the operations through
     * which an `InboxStorage` writes and removes the record with the given ID.
     *
     * @see GatedRecords
     */
    fun hold(id: Any, gate: Gate) {
        gates[id] = gate
    }

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        recordSpec: RecordSpec<I, R>,
        group: StorageGroup?
    ): RecordStorage<I, R> {
        val records = delegate.createRecordStorage(context, recordSpec, group)
        return GatedRecords(context, delegate = records, gates = gates)
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}

/**
 * A record storage which makes the calling thread pass the gate of a record
 * in `write(RecordWithColumns)`, `writeAll()` and `deleteAll()` — the three methods
 * through which an `InboxStorage` writes and removes its messages.
 *
 * The other methods go straight to the delegate.
 *
 * @param I The type of the record identifiers.
 * @param R The type of the stored records.
 *
 * @param context The specification of the context in which the storage is used.
 * @param delegate The storage doing the actual work.
 * @property gates The gates by the identifiers of the records. A record without
 *   a gate is written and deleted right away.
 */
private class GatedRecords<I : Any, R : Message>(
    context: ContextSpec,
    delegate: RecordStorage<I, R>,
    private val gates: Map<Any, Gate>
) : DelegatingRecordStorage<I, R>(context, delegate) {

    override fun write(record: RecordWithColumns<I, R>) {
        passGateOf(record.id())
        super.write(record)
    }

    override fun writeAll(records: Iterable<RecordWithColumns<I, R>>) {
        records.forEach { passGateOf(it.id()) }
        super.writeAll(records)
    }

    override fun deleteAll(ids: Iterable<I>) {
        ids.forEach { passGateOf(it) }
        super.deleteAll(ids)
    }

    private fun passGateOf(id: I) {
        gates[id]?.pass()
    }
}
