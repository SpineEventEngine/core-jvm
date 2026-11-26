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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.client.CompositeEntityStateFilter.either
import io.spine.client.CompositeFilter.CompositeOperator.EITHER
import io.spine.client.EntityStateFilter.eq
import io.spine.test.client.TestEntity
import io.spine.test.client.TestEntityId
import io.spine.test.client.testEntityId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`SubscriptionRequest` should")
internal class SubscriptionRequestSpec {

    private val server = StubSubscriptionServer { /* Keep the stream of updates open. */ }

    @AfterEach
    fun stopServer() = server.close()

    @Test
    fun `subscribe to the entities whose states pass a composite filter`() {
        val subscription = server.client.asGuest()
            .subscribeTo(TestEntity::class.java)
            .where(
                either(eq(TestEntity.Field.firstField(), "a"), eq(TestEntity.Field.thirdField(), 3))
            )
            .observe { }
            .onStreamingError { }
            .post()

        val target = subscription.topic.getTarget(0)
        target.filters.filterList.single().operator shouldBe EITHER
    }

    @Test
    fun `keep every consumer of no longer matching entities`() {
        val id = testEntityId { value = 1 }
        val reported = CountDownLatch(2)
        val stoppedMatching = CopyOnWriteArrayList<TestEntityId>()
        val otherStoppedMatching = CopyOnWriteArrayList<TestEntityId>()

        StubSubscriptionServer { updates ->
            updates.onNext(entityUpdatesOf(noLongerMatching(id)))
        }.use { stub ->
            stub.client.asGuest()
                .subscribeTo(TestEntity::class.java)
                .observe { }
                .whenNoLongerMatching(TestEntityId::class.java) {
                    stoppedMatching += it
                    reported.countDown()
                }
                .whenNoLongerMatching(TestEntityId::class.java) {
                    otherStoppedMatching += it
                    reported.countDown()
                }
                .onStreamingError { }
                .post()

            reported.await(TIMEOUT, SECONDS) shouldBe true
        }
        stoppedMatching shouldContainExactly listOf(id)
        otherStoppedMatching shouldContainExactly listOf(id)
    }

    private companion object {

        /**
         * The number of seconds to wait for the updates of the subscription.
         */
        const val TIMEOUT = 5L
    }
}
