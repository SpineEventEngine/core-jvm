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

import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Subscriptions
import io.spine.client.TopicId
import io.spine.test.event.ProjectCreated
import io.spine.testing.client.TestActorRequestFactory
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`TenantSubscriptionRegistry` should")
internal class TenantSubscriptionRegistrySpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val registry = TenantSubscriptionRegistry()
    private val subscription =
        Subscriptions.from(requests.topic().allOf(ProjectCreated::class.java))

    /**
     * The subscription as a client may send it back: with the same ID, and another topic ID.
     */
    private val changed: Subscription = subscription.toBuilder()
        .setTopic(
            subscription.topic.toBuilder()
                .setId(TopicId.newBuilder().setValue("t-changed-by-the-client"))
        )
        .build()

    @Test
    fun `find a subscription by its ID, whatever its other fields hold`() {
        registry.add(subscription, setOf(0))

        registry.containsId(changed.id) shouldBe true
    }

    @Test
    fun `activate a subscription by its ID`() {
        registry.add(subscription, setOf(0))

        registry.activate(changed, newCallback()) shouldBe true

        registry.byType(PROJECT_CREATED).shouldBeSingleton { it.isActive shouldBe true }
    }

    @Test
    fun `refuse to activate a subscription it does not have`() {
        registry.activate(subscription, newCallback()) shouldBe false

        registry.containsId(subscription.id) shouldBe false
    }

    @Test
    fun `refuse to activate a subscription removed from it`() {
        val callback = newCallback()
        registry.add(subscription, setOf(0))
        registry.activate(subscription, callback)
        registry.removeIfActivatedWith(subscription, callback)

        registry.activate(subscription, newCallback()) shouldBe false
    }

    @Test
    fun `remove a subscription by its ID, returning its record`() {
        registry.add(subscription, setOf(0))

        registry.remove(changed).shouldBePresent()

        registry.containsId(subscription.id) shouldBe false
        registry.hasType(PROJECT_CREATED) shouldBe false
    }

    @Test
    fun `ignore the removal of a subscription it does not have`() {
        registry.remove(subscription).shouldBeEmpty()

        registry.containsId(subscription.id) shouldBe false
    }

    @Test
    fun `remove a subscription activated with the given callback`() {
        val callback = newCallback()
        registry.add(subscription, setOf(0))
        registry.activate(subscription, callback)

        registry.removeIfActivatedWith(changed, callback).shouldBePresent()

        registry.containsId(subscription.id) shouldBe false
        registry.hasType(PROJECT_CREATED) shouldBe false
    }

    @Test
    fun `keep a subscription activated with another callback`() {
        val callback = newCallback()
        val other = newCallback()
        registry.add(subscription, setOf(0))
        registry.activate(subscription, callback)
        registry.activate(subscription, other)

        registry.removeIfActivatedWith(subscription, callback).shouldBeEmpty()

        registry.containsId(subscription.id) shouldBe true
        registry.hasType(PROJECT_CREATED) shouldBe true
    }

    @Test
    fun `keep a subscription that is not activated`() {
        registry.add(subscription, setOf(0))

        registry.removeIfActivatedWith(subscription, newCallback()).shouldBeEmpty()

        registry.containsId(subscription.id) shouldBe true
    }

    private companion object {
        val PROJECT_CREATED: TypeUrl = TypeUrl.of(ProjectCreated::class.java)
    }
}

/**
 * Creates a callback that ignores the updates.
 *
 * Each call returns a new object, as the registry tells callbacks apart by identity.
 */
private fun newCallback(): SubscriptionCallback = object : SubscriptionCallback {
    override fun accept(update: SubscriptionUpdate) = Unit
}
