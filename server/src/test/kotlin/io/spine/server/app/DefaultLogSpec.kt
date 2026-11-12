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

import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.WARNING
import io.spine.test.projection.Project
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.LoggingTest
import io.spine.type.toCompactJson
import java.util.logging.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@DisplayName("`DefaultLog` should")
internal class DefaultLogSpec : LoggingTest(DefaultLog::class.java, Level.INFO) {

    private val log = DefaultLog()

    private val event = unsupportedRequestRejected {
        request = refusedRequest {
            query = TestActorRequestFactory(DefaultLogSpec::class.java).query()
                .all(Project::class.java)
        }
        reason = "No Bounded Context serves the type of the query."
    }

    @BeforeEach
    fun interceptLog() {
        interceptLogging()
    }

    @AfterEach
    fun restoreLog() {
        restoreLogging()
    }

    @Test
    fun `write a message at the given level, starting with the logging domain`() {
        log.log(WARNING) { "A request was refused." }

        val record = assertLog().record()
        record.hasLevelThat().isEqualTo(Level.WARNING)
        record.hasMessageThat().startsWith("[Application] A request was refused.")
    }

    @Test
    fun `write an event as the name of its type, followed by its compact JSON form`() {
        log.logEvent(ERROR) { event }

        val record = assertLog().record()
        record.hasLevelThat().isEqualTo(Level.SEVERE)
        record.hasMessageThat().isEqualTo(
            "[Application] spine.server.app.UnsupportedRequestRejected ${event.toCompactJson()}"
        )
    }

    @Test
    fun `write a message and an event in two lines of one record`() {
        log.logEvent(WARNING, { "A request was refused." }) { event }

        val expected = "[Application] A request was refused." + System.lineSeparator() +
                "spine.server.app.UnsupportedRequestRejected ${event.toCompactJson()}"
        assertLog().record().hasMessageThat().isEqualTo(expected)
    }

    @Test
    fun `not call the functions for a level that is not enabled`() {
        log.log(DEBUG) { fail("The message must not be created.") }
        log.logEvent(DEBUG) { fail("The event must not be created.") }
        log.logEvent(DEBUG, { fail("The message must not be created.") }) {
            fail("The event must not be created.")
        }

        assertLog().isEmpty()
    }
}
