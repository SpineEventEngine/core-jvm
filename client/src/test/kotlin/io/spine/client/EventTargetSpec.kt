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
import io.spine.client.CompositeEventFilter.either
import io.spine.client.CompositeFilter.CompositeOperator.EITHER
import io.spine.client.EventFilter.eq
import io.spine.core.EventId
import io.spine.core.UserId
import io.spine.test.client.ClProjectCreated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`EventTarget` should")
internal class EventTargetSpec {

    @Test
    fun `match the events with the given IDs`() {
        val first = eventId("first")
        val second = eventId("second")

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
    fun `match the events that pass a composite filter`() {
        val target = newTarget().where(
            either(eq(ClProjectCreated.Field.id(), "a"), eq(ClProjectCreated.Field.id(), "b"))
        )

        built(target).filters.filterList.single().operator shouldBe EITHER
    }

    @Test
    fun `refuse to build a target without consumers`() {
        val error = shouldThrow<IllegalStateException> {
            newTarget().toObserver(consumingErrors = null)
        }
        error.message shouldContain "has no consumers"
    }

    private fun newTarget(): EventTarget<ClProjectCreated> =
        EventTarget(ClProjectCreated::class.java, topics.select(ClProjectCreated::class.java))

    private fun built(target: EventTarget<ClProjectCreated>): Target =
        target.observe { }
            .toObserver(consumingErrors = null)
            .target

    private fun idsOf(target: EventTarget<ClProjectCreated>): List<Any> =
        built(target).filters.idFilter.idList.map { Identifier.unpack(it) }

    private fun eventId(value: String): EventId =
        EventId.newBuilder()
            .setValue(value)
            .build()

    private companion object {

        val topics: TopicFactory = actorRequestFactory(
            UserId.newBuilder().setValue("event-target-spec").build()
        ).topic()
    }
}
