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

import io.spine.base.EventMessage
import io.spine.base.Identifier
import io.spine.core.EventId
import io.spine.core.MessageId
import io.spine.server.delivery.CatchUpId
import io.spine.server.delivery.CatchUpSignal
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxStorage
import io.spine.server.delivery.event.EntityPreparedForCatchUp
import io.spine.server.storage.memory.InMemoryStorageFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A single-tenant, in-memory inbox storage that records each message sent to an inbox.
 *
 * A message sent is stored on its own. A delivery run stores the messages it has
 * processed in a batch, which is not recorded.
 */
class RecordingInboxStorage :
    InboxStorage(InMemoryStorageFactory.newInstance(), /* multitenant = */ false) {

    private val sent = CopyOnWriteArrayList<InboxMessage>()

    override fun write(message: InboxMessage) {
        sent.add(message)
        super.write(message)
    }

    /**
     * Returns the messages sent to any inbox, in the order they were sent.
     */
    fun messagesSent(): List<InboxMessage> = sent.toList()

    /**
     * Returns the IDs of the events sent to any inbox.
     */
    fun eventIds(): List<EventId> =
        sent.filter { it.hasEvent() }.map { it.event.id }

    /**
     * Returns the events sent to any inbox.
     */
    fun events(): List<EventMessage> =
        sent.filter { it.hasEvent() }.map { it.event.enclosedMessage() }

    /**
     * Returns the catch-up signals of the given job sent to any inbox.
     */
    fun signalsOf(job: CatchUpId): List<CatchUpSignal> =
        events().filterIsInstance<CatchUpSignal>().filter { it.id == job }

    /**
     * Returns the IDs of the instances that reported being prepared for the given job,
     * in the order of the reports.
     *
     * The instances must have `String` IDs, as [CounterView] projections do.
     */
    fun reportsOf(job: CatchUpId): List<String> =
        signalsOf(job).filterIsInstance<EntityPreparedForCatchUp>()
            .map { Identifier.unpack(it.instanceId, MessageId::class.java) }
            .map { Identifier.unpack(it.id, String::class.java) }
}
