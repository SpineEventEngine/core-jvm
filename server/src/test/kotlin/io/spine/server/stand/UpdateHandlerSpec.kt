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

package io.spine.server.stand

import io.spine.client.Targets.allOf
import io.spine.core.EventId
import io.spine.server.stand.given.SubscriptionRecordTestEnv.projectCreatedEnvelope
import io.spine.server.stand.given.SubscriptionRecordTestEnv.subscription
import io.spine.test.event.ProjectCreated
import io.spine.testing.logging.LoggingTest
import java.util.logging.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`UpdateHandler` should")
internal class UpdateHandlerSpec : LoggingTest(EventUpdateHandler::class.java, Level.SEVERE) {

    private val subscription = subscription(allOf(ProjectCreated::class.java))
    private val handler = EventUpdateHandler(subscription, 0)

    @BeforeEach
    fun interceptLog() {
        interceptLogging()
    }

    @AfterEach
    fun restoreLog() {
        restoreLogging()
    }

    @Test
    fun `log the failure of the callback, naming the subscription`() {
        handler.setCallback { error("The callback fails on purpose.") }
        val eventId = EventId.newBuilder().setValue("update-handler-event").build()

        handler.handle(projectCreatedEnvelope(eventId))

        val record = assertLog().record()
        record.hasLevelThat().isEqualTo(Level.SEVERE)
        record.hasMessageThat().contains(subscription.id.value)
    }
}
