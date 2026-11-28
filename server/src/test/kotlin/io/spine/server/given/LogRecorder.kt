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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * Records what the logger of the given class, and the loggers of its nested classes, log at
 * the given level and above, until closed.
 *
 * Unlike `LoggingTest`, it keeps every record, so a spec can find one message among others.
 *
 * @param cls The class whose logger to record.
 * @param level The lowest level of the records to keep.
 */
internal class LogRecorder(cls: Class<*>, level: Level) : AutoCloseable {

    private val logger = Logger.getLogger(cls.name)
    private val previousLevel: Level? = logger.level
    private val records = CopyOnWriteArrayList<LogRecord>()
    private val formatter = SimpleFormatter()

    private val handler = object : Handler() {
        override fun publish(record: LogRecord) {
            records.add(record)
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    init {
        handler.level = level
        logger.level = level
        logger.addHandler(handler)
    }

    /**
     * The formatted messages of the kept records, in the order of logging.
     */
    val messages: List<String>
        get() = records.map { formatter.formatMessage(it) }

    /**
     * Stops recording, and restores the level of the logger.
     */
    override fun close() {
        logger.removeHandler(handler)
        logger.level = previousLevel
    }
}
