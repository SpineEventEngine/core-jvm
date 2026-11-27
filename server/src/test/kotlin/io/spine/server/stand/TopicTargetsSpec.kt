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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.client.Targets.allOf
import io.spine.client.Topic
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.subscriptionservice.event.ReportSent
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Topic.checkHasTarget()` should")
internal class TopicTargetsSpec {

    /**
     * A topic with two targets.
     */
    private val topic: Topic = TestActorRequestFactory(javaClass).topic().forTargets(
        listOf(allOf(ReportSent::class.java), allOf(CTaskCreated::class.java))
    )

    @Test
    fun `pass the index of each target`() {
        shouldNotThrowAny {
            topic.checkHasTarget(0)
            topic.checkHasTarget(1)
        }
    }

    @Test
    fun `refuse a negative index, naming it`() {
        val error = shouldThrow<IllegalArgumentException> {
            topic.checkHasTarget(-1)
        }
        error.message shouldBe "The topic has no target with the index -1."
    }

    @Test
    fun `refuse the index past the last target`() {
        shouldThrow<IllegalArgumentException> {
            topic.checkHasTarget(2)
        }
    }
}
