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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.client.Subscriptions
import io.spine.client.Targets.allOf
import io.spine.test.event.ProjectCreated
import io.spine.test.event.TaskAdded
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Stand`, for a topic with several targets, should")
internal class StandMultiTargetSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val stand = Stand.newBuilder().build()
    private val subscription = Subscriptions.from(
        requests.topic().forTargets(
            listOf(allOf(ProjectCreated::class.java), allOf(TaskAdded::class.java))
        )
    )

    @Test
    fun `hold a subscription for some of the targets of its topic`() {
        stand.subscribe(subscription, setOf(1))

        stand.hasSubscription(subscription) shouldBe true
    }

    @Test
    fun `refuse a subscription it already has`() {
        stand.subscribe(subscription, setOf(0))

        shouldThrow<IllegalArgumentException> {
            stand.subscribe(subscription, setOf(1))
        }
    }

    @Test
    fun `refuse a target that the topic does not have`() {
        shouldThrow<IllegalArgumentException> {
            stand.subscribe(subscription, setOf(2))
        }
        stand.hasSubscription(subscription) shouldBe false
    }
}
