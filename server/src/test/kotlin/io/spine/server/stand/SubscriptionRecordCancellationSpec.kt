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

import io.kotest.matchers.shouldBe
import io.spine.client.SubscriptionUpdate
import io.spine.client.Targets.allOf
import io.spine.core.EventId
import io.spine.server.stand.given.SubscriptionRecordTestEnv.projectCreatedEnvelope
import io.spine.server.stand.given.SubscriptionRecordTestEnv.subscription
import io.spine.server.type.EventEnvelope
import io.spine.test.event.ProjectCreated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionRecordTest` with the specs of a subscription that ends for
 * a callback.
 *
 * The topic has two targets of one type, so an event produces two updates.
 */
@DisplayName("`SubscriptionRecord`, when the subscription ends for a callback, should")
internal class SubscriptionRecordCancellationSpec {

    private val record = SubscriptionRecord.of(
        subscription(allOf(ProjectCreated::class.java), allOf(ProjectCreated::class.java))
    )
    private val callback = RecordingCallback()

    @Test
    fun `pass no updates to the callback once it is told about the cancellation`() {
        record.activate(callback)

        record.notifyCancelled()
        record.update(projectCreated())

        callback.cancellations shouldBe 1
        callback.updates shouldBe 0
    }

    @Test
    fun `pass no updates once the subscription is released, telling the callback nothing`() {
        record.activate(callback)

        record.release()
        record.update(projectCreated())

        callback.cancellations shouldBe 0
        callback.updates shouldBe 0
    }

    @Test
    fun `stop passing an event to the targets once the callback cancels the subscription`() {
        val cancelling = RecordingCallback(onUpdate = { record.notifyCancelled() })
        record.activate(cancelling)

        record.update(projectCreated())

        cancelling.updates shouldBe 1
        cancelling.cancellations shouldBe 1
    }

    @Test
    fun `tell the callback of an earlier activation that it serves the subscription no more`() {
        val latest = RecordingCallback()
        record.activate(callback)

        record.activate(latest)
        record.update(projectCreated())

        callback.cancellations shouldBe 1
        callback.updates shouldBe 0
        latest.cancellations shouldBe 0
        latest.updates shouldBe 2
    }

    @Test
    fun `not tell the callback when it activates the subscription again`() {
        record.activate(callback)

        record.activate(callback)

        callback.cancellations shouldBe 0
    }

    @Test
    fun `refuse to be activated once the subscription is cancelled`() {
        record.activate(callback)
        record.notifyCancelled()
        val latest = RecordingCallback()

        record.activate(latest) shouldBe false

        record.update(projectCreated())
        latest.updates shouldBe 0
        latest.cancellations shouldBe 0
    }

    private fun projectCreated(): EventEnvelope =
        projectCreatedEnvelope(EventId.newBuilder().setValue("record-cancellation").build())
}

/**
 * Counts the updates and the cancellations it receives.
 *
 * @param onUpdate The action to run after counting an update.
 */
private class RecordingCallback(private val onUpdate: () -> Unit = {}) : SubscriptionCallback {

    var updates = 0
        private set

    var cancellations = 0
        private set

    override fun accept(update: SubscriptionUpdate) {
        updates++
        onUpdate()
    }

    override fun onCancelled() {
        cancellations++
    }
}
