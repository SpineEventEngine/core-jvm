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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.spine.client.Filters.gt
import io.spine.client.SubscriptionUpdate
import io.spine.client.Subscriptions
import io.spine.client.Topic
import io.spine.core.Event
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.integration.DocumentId
import io.spine.server.integration.OpenOfficeDocumentUploaded
import io.spine.server.integration.openOfficeDocumentUploaded
import io.spine.server.type.EventEnvelope
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`Stand`, when a filter of a subscription fails on an event, should")
internal class StandFilterFailureSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val stand = Stand.newBuilder().build()

    @Test
    fun `not fail on the event`() {
        activate(unmatchable()) {}

        shouldNotThrowAny {
            stand.eventListener().accept(EventEnvelope.of(uploaded()))
        }
    }

    @Test
    fun `pass the event to the other subscriptions to its type`() {
        val updates = mutableListOf<SubscriptionUpdate>()
        activate(unmatchable()) {}
        activate(requests.topic().allOf(OpenOfficeDocumentUploaded::class.java)) { updates.add(it) }
        activate(unmatchable()) {}
        val event = uploaded()

        stand.eventListener().accept(EventEnvelope.of(event))

        updates.flatMap { it.eventUpdates.eventList } shouldContainExactly listOf(event)
    }

    /**
     * Subscribes the given callback to the given topic, and activates the subscription.
     */
    private fun activate(topic: Topic, callback: SubscriptionCallback) {
        val subscription = Subscriptions.from(topic)
        stand.subscribe(subscription, setOf(0))
        stand.activate(subscription, callback, noOpObserver())
    }

    /**
     * Creates a topic of uploaded documents whose filter compares the text of a document
     * to a number, which fails on each event.
     */
    private fun unmatchable(): Topic =
        requests.topic()
            .select(OpenOfficeDocumentUploaded::class.java)
            .where(gt("text", 42))
            .build()

    private fun uploaded(): Event =
        TestEventFactory.newInstance(requests)
            .createEvent(openOfficeDocumentUploaded {
                id = DocumentId.generate()
                text = "A document"
            })
}
