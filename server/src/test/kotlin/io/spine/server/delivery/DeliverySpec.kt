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

package io.spine.server.delivery

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.core.BoundedContextName
import io.spine.core.BoundedContextNames
import io.spine.core.TenantId
import io.spine.environment.Tests
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.given.CounterView
import io.spine.server.delivery.given.NoOpEndpoint
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.successfulOutcome
import io.spine.server.nodeId
import io.spine.server.serverEnvironment
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.type.CommandEnvelope
import io.spine.test.delivery.DTask
import io.spine.test.delivery.dCreateTask
import io.spine.test.delivery.numberAdded
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.server.blackbox.BlackBox
import io.spine.testing.server.buildForTests
import io.spine.type.TypeUrl
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("`Delivery` should")
internal class DeliverySpec : AbstractDeliveryTest() {

    @AfterEach
    fun restoreDefaultWeight() {
        CounterView.changeWeightTo(1)
    }

    @Test
    fun `stop delivering a shard once the monitor tells to`() {
        // No shard observer, so the messages wait in the shard until delivered below.
        val storage = InMemoryStorageFactory.newInstance()
        val delivery = Delivery.newBuilder(nodeId { value = newUuid() }, storage)
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setPageSize(1)
            .setMonitor(StopAfterEachPage())
            .build()
        val environment = serverEnvironment {
            under<Tests> {
                use(storage)
                use(delivery)
            }
        }
        // The weight is static, and `CatchUpTest` leaves it at 100.
        CounterView.changeWeightTo(1)
        val repository = CounterView.Repository()
        val context = BoundedContextBuilder.assumingTests()
            .add(repository)
            .buildForTests(environment)
        BlackBox.from(context).use { box ->
            repeat(MESSAGES) {
                box.receivesEvent(numberAdded { calculatorId = TARGET; value = 0 })
            }

            val stats = TenantAwareRunner.with(TenantId.getDefaultInstance()).evaluate {
                delivery.deliverMessagesFrom(SHARD)
            }

            stats.shouldBePresent().deliveredCount() shouldBe 1
            repository.find(TARGET).getOrNull()?.state()?.total shouldBe 1
            InboxContents.of(delivery).values.flatten().count { it.status == TO_DELIVER } shouldBe
                    MESSAGES - 1
        }
    }

    @Test
    fun `store a message with the context of the inbox that received it`() {
        // No shard observer, so the message waits in the shard.
        val delivery = Delivery.newBuilder(nodeId(), storageFactory())
            .setStrategy(UniformAcrossAllShards.singleShard())
            .build()
        use(delivery)
        val builder = BoundedContext.singleTenant(COUNTERS).add(CounterView.Repository())
        blackBox(builder).use { box ->
            box.receivesEvent(numberAdded { calculatorId = TARGET; value = 0 })

            val stored = InboxContents.of(delivery).values.flatten()
                .filter { it.inboxId.typeUrl == CounterView.projectionType().value() }
            stored.map { it.context.value } shouldContainExactly listOf(COUNTERS)
        }
    }

    /**
     * Sends one command through the inboxes of one entity type in two contexts.
     *
     * In one application, two open contexts never serve one entity type, and each System
     * context creates its own signals. So the test builds the inboxes directly.
     */
    @ParameterizedTest(name = "with pages of {0}")
    @ValueSource(ints = [1, 2])
    fun `deliver one signal to the same target in each of two contexts`(pageSize: Int) {
        // No shard observer, so the messages wait in the shard until delivered below.
        val delivery = Delivery.newBuilder(nodeId(), InMemoryStorageFactory.newInstance())
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setPageSize(pageSize)
            .build()
        val received = mutableListOf<String>()
        val inboxes = listOf(FIRST, SECOND).map { inboxIn(delivery, it, received) }
        val command = CommandEnvelope.of(requests.createCommand(dCreateTask { id = TARGET }))

        inboxes.forEach { it.send(command).toHandler(TARGET) }
        TenantAwareRunner.with(TenantId.getDefaultInstance()).run {
            delivery.deliverMessagesFrom(SHARD)
        }

        received shouldContainExactlyInAnyOrder listOf(FIRST.value, SECOND.value)
    }

    /**
     * Creates an inbox of tasks in the given context, which records the name of the context
     * on each dispatched command.
     */
    private fun inboxIn(
        delivery: Delivery,
        context: BoundedContextName,
        received: MutableList<String>
    ): Inbox<String> =
        delivery.newInbox<String>(TypeUrl.of(DTask::class.java), context)
            .addCommandEndpoint(InboxLabel.HANDLE_COMMAND) {
                RecordingEndpoint(it, context, received)
            }
            .build()

    /**
     * Records the name of the given context when the given command is dispatched.
     */
    private class RecordingEndpoint(
        private val command: CommandEnvelope,
        private val context: BoundedContextName,
        private val received: MutableList<String>
    ) : NoOpEndpoint() {

        override fun dispatchTo(targetId: String): DispatchOutcome {
            received.add(context.value)
            return successfulOutcome(command)
        }
    }

    /**
     * Stops a delivery run after its first page.
     */
    private class StopAfterEachPage : DeliveryMonitor() {

        override fun shouldContinueAfter(stage: DeliveryStage): Boolean = false
    }

    private companion object {
        const val TARGET = "target"
        const val COUNTERS = "Counters"
        const val MESSAGES = 3
        val SHARD: ShardIndex = DeliveryStrategy.newIndex(0, 1)
        val FIRST: BoundedContextName = BoundedContextNames.newName("First")
        val SECOND: BoundedContextName = BoundedContextNames.newName("Second")
        val requests = TestActorRequestFactory(DeliverySpec::class.java)
    }
}
