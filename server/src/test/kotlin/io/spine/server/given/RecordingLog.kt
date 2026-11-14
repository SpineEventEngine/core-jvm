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

package io.spine.server.given

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.EventMessage
import io.spine.logging.Level
import io.spine.server.app.ApplicationLog
import java.util.Collections.synchronizedList

/**
 * An [ApplicationLog] that keeps its records in memory.
 */
internal class RecordingLog : ApplicationLog {

    /**
     * A record of the log.
     *
     * @param level The level of the record.
     * @param message The message of the record, or `null` if the record has none.
     * @param event The event of the record, or `null` if the record has none.
     */
    data class Record(
        val level: Level,
        val message: String?,
        val event: EventMessage?
    )

    private val recorded = synchronizedList(mutableListOf<Record>())

    /**
     * The records of the log, in the order of writing.
     */
    val records: List<Record>
        get() = synchronized(recorded) { recorded.toList() }

    /**
     * The events of the records, in the order of writing.
     */
    val events: List<EventMessage>
        get() = records.mapNotNull { it.event }

    /**
     * Obtains the only record, checking that its event is of the type [E].
     */
    inline fun <reified E : EventMessage> single(): Pair<Level, E> {
        val records = records
        records shouldHaveSize 1
        val record = records.single()
        return record.level to record.event.shouldBeInstanceOf<E>()
    }

    override fun log(level: Level, message: () -> String) {
        recorded.add(Record(level, message(), null))
    }

    override fun logEvent(level: Level, event: () -> EventMessage) {
        recorded.add(Record(level, null, event()))
    }

    override fun logEvent(level: Level, message: () -> String, event: () -> EventMessage) {
        recorded.add(Record(level, message(), event()))
    }
}
