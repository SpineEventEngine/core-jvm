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

package io.spine.server.app

import io.spine.base.EventMessage
import io.spine.logging.Level
import io.spine.logging.LoggingDomain
import io.spine.logging.LoggingFactory.loggerFor
import io.spine.type.TypeName
import io.spine.type.toCompactJson

/**
 * The [ApplicationLog] that writes with Spine Logging.
 *
 * The [logging domain][LoggingDomain] of the class starts the message of each record with
 * `[Application]`, so that the records of the application stand out among the others.
 * The name of the logger is the name of this class, so a logging backend may also route
 * the records apart from the others by the name of the package. Applications configure their
 * logging with this name, so the class stays in its package.
 *
 * An event is written as the name of its type, followed by its compact JSON form. For example:
 * ```
 * spine.server.app.UnsupportedRequestRejected {"request":{...},"reason":"..."}
 * ```
 */
@LoggingDomain("Application")
internal class DefaultLog : ApplicationLog {

    override fun log(level: Level, message: () -> String) {
        logger.at(level).log(message)
    }

    override fun logEvent(level: Level, event: () -> EventMessage) {
        logger.at(level).log { event().printed() }
    }

    /**
     * Writes a record of two lines: the message, and the event.
     */
    override fun logEvent(level: Level, message: () -> String, event: () -> EventMessage) {
        logger.at(level).log { message() + System.lineSeparator() + event().printed() }
    }

    private companion object {

        /**
         * The logger of the class, which applies its [LoggingDomain].
         */
        private val logger = loggerFor(DefaultLog::class)
    }
}

/**
 * Prints this event for a record of the log: the name of its type, followed by
 * its compact JSON form.
 */
private fun EventMessage.printed(): String = "${TypeName.of(this)} ${toCompactJson()}"
