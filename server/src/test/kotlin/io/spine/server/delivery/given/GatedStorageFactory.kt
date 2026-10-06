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

package io.spine.server.delivery.given

import com.google.protobuf.Message
import io.spine.server.ContextSpec
import io.spine.server.entity.given.concurrency.Gate
import io.spine.server.storage.DelegatingRecordStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.RecordWithColumns
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Creates in-memory record storages that can hold a thread at a [Gate].
 *
 * A thread writing or deleting a record selected by a test stops inside the record
 * storage — where a real storage does its I/O — until the gate opens. A test selects
 * the record by its ID with [hold], or the next record written with [holdNextWrite].
 */
internal class GatedStorageFactory : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()
    private val gates = ConcurrentHashMap<Any, Gate>()
    private val nextWrite = AtomicReference<Gate?>()

    /**
     * Makes the record storages hold, at the given gate, the operations through
     * which an `InboxStorage` writes and removes the record with the given ID.
     *
     * @see GatedRecords
     */
    fun hold(id: Any, gate: Gate) {
        gates[id] = gate
    }

    /**
     * Makes the next write of a single record pass the given gate, whatever the record.
     */
    fun holdNextWrite(gate: Gate) {
        nextWrite.set(gate)
    }

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        recordSpec: RecordSpec<I, R>,
        group: StorageGroup?
    ): RecordStorage<I, R> {
        val records = delegate.createRecordStorage(context, recordSpec, group)
        return GatedRecords(context, delegate = records, gates = gates, nextWrite = nextWrite)
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}

/**
 * A record storage that makes the calling thread pass the gate of a record
 * in `write(RecordWithColumns)`, `writeAll()` and `deleteAll()` — the three methods
 * through which an `InboxStorage` writes and removes its messages.
 *
 * Before writing a single record, the thread also passes the gate set for the next
 * write, if any. The other methods go straight to the delegate.
 *
 * @param I The type of the record identifiers.
 * @param R The type of the stored records.
 *
 * @param context The specification of the context in which the storage is used.
 * @param delegate The storage doing the actual work.
 * @property gates The gates by the identifiers of the records. A record without
 *   a gate is written and deleted right away.
 * @property nextWrite The gate for the next write of a single record to pass.
 */
private class GatedRecords<I : Any, R : Message>(
    context: ContextSpec,
    delegate: RecordStorage<I, R>,
    private val gates: Map<Any, Gate>,
    private val nextWrite: AtomicReference<Gate?>
) : DelegatingRecordStorage<I, R>(context, delegate) {

    override fun write(record: RecordWithColumns<I, R>) {
        nextWrite.getAndSet(null)?.pass()
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
