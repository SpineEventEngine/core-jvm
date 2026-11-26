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

package io.spine.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier
import io.spine.client.CompositeEntityStateFilter.either
import io.spine.client.CompositeFilter.CompositeOperator.EITHER
import io.spine.client.EntityStateFilter.eq
import io.spine.core.userId
import io.spine.test.client.TestEntity
import io.spine.test.client.TestEntityId
import io.spine.test.client.testEntity
import io.spine.test.client.testEntityId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`StateTarget` should")
internal class StateTargetSpec {

    @Test
    fun `match the entities with the given IDs`() {
        val first = testEntityId { value = 1 }
        val second = testEntityId { value = 2 }

        idsOf(newTarget().byId(first, second)) shouldContainExactly listOf(first, second)
        idsOf(newTarget().byId(listOf(second))) shouldContainExactly listOf(second)
    }

    @Test
    fun `accept the IDs of the simple types`() {
        idsOf(newTarget().byId("a", "b")) shouldContainExactly listOf("a", "b")
        idsOf(newTarget().byId(1, 2)) shouldContainExactly listOf(1, 2)
        idsOf(newTarget().byId(1L, 2L)) shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `match the entities whose states pass a composite filter`() {
        val target = newTarget().where(
            either(eq(TestEntity.Field.firstField(), "a"), eq(TestEntity.Field.thirdField(), 3))
        )

        built(target).filters.filterList.single().operator shouldBe EITHER
    }

    @Test
    fun `keep every consumer added to the target`() {
        val states = mutableListOf<TestEntity>()
        val otherStates = mutableListOf<TestEntity>()
        val stoppedMatching = mutableListOf<TestEntityId>()
        val otherStoppedMatching = mutableListOf<TestEntityId>()
        val observer = newTarget()
            .observe { states += it }
            .observe { otherStates += it }
            .whenNoLongerMatching(TestEntityId::class.java) { stoppedMatching += it }
            .whenNoLongerMatching(TestEntityId::class.java) { otherStoppedMatching += it }
            .toObserver(consumingErrors = null)
        val id = testEntityId { value = 1 }
        val state = testEntity {
            this.id = id
            firstField = "a"
        }

        observer.onUpdate(entityUpdatesOf(stateChanged(id, state), noLongerMatching(id)))

        states shouldContainExactly listOf(state)
        otherStates shouldContainExactly listOf(state)
        stoppedMatching shouldContainExactly listOf(id)
        otherStoppedMatching shouldContainExactly listOf(id)
    }

    @Test
    fun `refuse to build a target without consumers of states`() {
        val error = shouldThrow<IllegalStateException> {
            newTarget()
                .whenNoLongerMatching(TestEntityId::class.java) { }
                .toObserver(consumingErrors = null)
        }
        error.message shouldContain "has no consumers of states"
    }

    private fun newTarget(): StateTarget<TestEntity> =
        StateTarget(TestEntity::class.java, topics.select(TestEntity::class.java))

    private fun built(target: StateTarget<TestEntity>): Target =
        target.observe { }
            .toObserver(consumingErrors = null)
            .target

    private fun idsOf(target: StateTarget<TestEntity>): List<Any> =
        built(target).filters.idFilter.idList.map { Identifier.unpack(it) }

    private companion object {

        val topics: TopicFactory = actorRequestFactory(
            userId { value = "state-target-spec" }
        ).topic()
    }
}
