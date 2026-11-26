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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.spine.client.EntityStateUpdate
import io.spine.client.Filters.all
import io.spine.client.Filters.eq
import io.spine.client.SubscriptionUpdate
import io.spine.client.Targets.allOf
import io.spine.client.Targets.composeTarget
import io.spine.core.EventId
import io.spine.server.stand.given.SubscriptionRecordTestEnv.projectCreatedEnvelope
import io.spine.server.stand.given.SubscriptionRecordTestEnv.projectId
import io.spine.server.stand.given.SubscriptionRecordTestEnv.projectWithName
import io.spine.server.stand.given.SubscriptionRecordTestEnv.stateChangedEnvelope
import io.spine.server.stand.given.SubscriptionRecordTestEnv.subscription
import io.spine.test.aggregate.AggProject
import io.spine.test.event.ProjectCreated
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`SubscriptionRecord` with several targets should")
internal class SubscriptionRecordMultiTargetSpec {

    private val id = projectId("multi-target-project")

    /**
     * Targets all projects, the projects with the matching name, and the creation of
     * projects, in this order.
     */
    private val subscription = subscription(
        allOf(AggProject::class.java),
        composeTarget(AggProject::class.java, null, setOf(all(eq("name", MATCHING)))),
        allOf(ProjectCreated::class.java)
    )

    @Test
    fun `report a state that matches two targets of one type once for each of them`() {
        val record = SubscriptionRecord.of(subscription)
        val matching = projectWithName(MATCHING)

        val updates = record.detectUpdates(stateChangedEnvelope(id, matching, matching))

        updates.map { it.targetIndex } shouldContainExactly listOf(0, 1)
        updates.forEach { it.stateUpdate().hasState() shouldBe true }
    }

    @Test
    fun `report the updates in the order of the targets, whatever order the indexes come in`() {
        val record = SubscriptionRecord.of(subscription, linkedSetOf(1, 0))
        val matching = projectWithName(MATCHING)

        val updates = record.detectUpdates(stateChangedEnvelope(id, matching, matching))

        updates.map { it.targetIndex } shouldContainExactly listOf(0, 1)
    }

    @Test
    fun `report that a state no longer matches one target only for that target`() {
        val record = SubscriptionRecord.of(subscription)
        val before = projectWithName(MATCHING)
        val after = projectWithName("Renamed")

        val updates = record.detectUpdates(stateChangedEnvelope(id, before, after))

        updates.map { it.targetIndex } shouldContainExactly listOf(0, 1)
        updates[0].stateUpdate().hasState() shouldBe true
        updates[1].stateUpdate().noLongerMatching shouldBe true
    }

    @Test
    fun `report an event for the target of its type`() {
        val record = SubscriptionRecord.of(subscription)
        val eventId = EventId.newBuilder().setValue("multi-target-event").build()

        val updates = record.detectUpdates(projectCreatedEnvelope(eventId))

        updates.shouldBeSingleton {
            it.targetIndex shouldBe 2
            it.eventUpdates.eventCount shouldBe 1
        }
    }

    @Test
    fun `serve only the given targets`() {
        val record = SubscriptionRecord.of(subscription, setOf(1))
        val other = projectWithName("Other")

        record.detectUpdates(stateChangedEnvelope(id, other, other)).shouldBeEmpty()
        record.eventTypes() shouldNotContain PROJECT_CREATED_TYPE
    }

    @Test
    fun `refuse to serve no targets, or a target the topic does not have`() {
        shouldThrow<IllegalArgumentException> {
            SubscriptionRecord.of(subscription, setOf())
        }
        shouldThrow<IllegalArgumentException> {
            SubscriptionRecord.of(subscription, setOf(3))
        }
    }

    private companion object {
        const val MATCHING = "Matching"
        val PROJECT_CREATED_TYPE: TypeUrl = TypeUrl.of(ProjectCreated::class.java)
    }
}

/**
 * Obtains the only entity state update of this subscription update.
 */
private fun SubscriptionUpdate.stateUpdate(): EntityStateUpdate =
    entityUpdates.updateList.single()
