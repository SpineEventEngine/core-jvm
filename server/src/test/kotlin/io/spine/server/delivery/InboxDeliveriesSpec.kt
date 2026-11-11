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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.base.Identifier
import io.spine.core.BoundedContextName
import io.spine.core.BoundedContextNames.newName
import io.spine.server.delivery.given.NoOpEndpoint
import io.spine.test.delivery.TallyA1
import io.spine.type.TypeUrl
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`InboxDeliveries` should")
internal class InboxDeliveriesSpec : AbstractDeliveryTest() {

    private val deliveries = InboxDeliveries()

    @Nested inner class
    `find the inbox of an entity type in the context of the message` {

        @Test
        fun `by the type and the context`() {
            deliveries.register(inbox(TALLY_TYPE, TASKS))

            shouldNotThrowAny {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = TASKS))
            }
        }

        @Test
        fun `not the inbox of another context registered later`() {
            val toTasks = messageTo(TALLY_TYPE, "some-tally", context = TASKS)
            val toBilling = messageTo(TALLY_TYPE, "some-tally", context = BILLING)
            deliveries.register(inbox(TALLY_TYPE, TASKS))
            val ofTasks = deliveries.get(toTasks)

            deliveries.register(inbox(TALLY_TYPE, BILLING))

            deliveries.get(toTasks) shouldBeSameInstanceAs ofTasks
            deliveries.get(toBilling) shouldNotBeSameInstanceAs ofTasks
        }

        @Test
        fun `failing if the type has no inbox`() {
            val error = shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = TASKS))
            }
            error.message shouldContain TALLY_TYPE.value()
        }

        @Test
        fun `failing if only another context has an inbox of the type`() {
            deliveries.register(inbox(TALLY_TYPE, BILLING))

            val error = shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = TASKS))
            }
            error.message shouldContain TASKS.value
        }

        @Test
        fun `keeping the inbox that replaced the unregistered one`() {
            val first = inbox(TALLY_TYPE, TASKS)
            val second = inbox(TALLY_TYPE, TASKS)
            deliveries.register(first)
            deliveries.register(second)

            deliveries.unregister(first)
            shouldNotThrowAny {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = TASKS))
            }

            deliveries.unregister(second)
            shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = TASKS))
            }
        }

        @Test
        fun `keeping the inbox of another context when one is unregistered`() {
            val tasks = inbox(TALLY_TYPE, TASKS)
            deliveries.register(tasks)
            deliveries.register(inbox(TALLY_TYPE, BILLING))

            deliveries.unregister(tasks)

            shouldNotThrowAny {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = BILLING))
            }
            shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(TALLY_TYPE, "some-tally", context = TASKS))
            }
        }
    }

    @Nested inner class
    `find the inbox of a catch-up message in the System context of the projection type` {

        @Test
        fun `for a catch-up process`() {
            serveTallies(TASKS)
            deliveries.register(inbox(CatchUpProcess.TYPE, TASKS.toSystem()))

            shouldNotThrowAny {
                deliveries.get(messageTo(CatchUpProcess.TYPE, job(), context = TASKS.toSystem()))
            }
        }

        @Test
        fun `for a shard maintenance process`() {
            serveTallies(TASKS)
            deliveries.register(inbox(ShardMaintenanceProcess.TYPE, TASKS.toSystem()))

            shouldNotThrowAny {
                deliveries.get(
                    messageTo(
                        ShardMaintenanceProcess.TYPE,
                        maintenanceOf(job()),
                        context = TASKS.toSystem()
                    )
                )
            }
        }

        @Test
        fun `whatever context the message tells`() {
            serveTallies(TASKS)
            deliveries.register(inbox(CatchUpProcess.TYPE, TASKS.toSystem()))

            shouldNotThrowAny {
                deliveries.get(messageTo(CatchUpProcess.TYPE, job(), context = BILLING.toSystem()))
            }
        }

        @Test
        fun `failing if only another System context has an inbox`() {
            serveTallies(TASKS)
            deliveries.register(inbox(CatchUpProcess.TYPE, BILLING.toSystem()))

            val error = shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(CatchUpProcess.TYPE, job(), context = TASKS.toSystem()))
            }
            error.message shouldContain TASKS.toSystem().value
        }

        @Test
        fun `keeping the inbox of a System context that replaced the unregistered one`() {
            serveTallies(TASKS)
            val first = inbox(CatchUpProcess.TYPE, TASKS.toSystem())
            val second = inbox(CatchUpProcess.TYPE, TASKS.toSystem())
            deliveries.register(first)
            deliveries.register(second)

            deliveries.unregister(first)

            shouldNotThrowAny {
                deliveries.get(messageTo(CatchUpProcess.TYPE, job(), context = TASKS.toSystem()))
            }
        }
    }

    @Nested inner class
    `fail to find the inbox of a catch-up message` {

        @Test
        fun `if no inbox is registered for the projection type`() {
            deliveries.register(inbox(CatchUpProcess.TYPE, TASKS.toSystem()))

            val error = shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(CatchUpProcess.TYPE, job(), context = TASKS.toSystem()))
            }
            error.message shouldContain TALLY_TYPE.value()
        }

        @Test
        fun `if the projection type has inboxes in several contexts`() {
            serveTallies(TASKS)
            serveTallies(BILLING)
            deliveries.register(inbox(CatchUpProcess.TYPE, TASKS.toSystem()))

            val error = shouldThrow<IllegalStateException> {
                deliveries.get(messageTo(CatchUpProcess.TYPE, job(), context = TASKS.toSystem()))
            }
            error.message shouldContain "several Bounded Contexts"
        }

        @Test
        fun `if a shard maintenance message is addressed to the shard only`() {
            serveTallies(TASKS)
            val shardOnly = DeliveryStrategy.newIndex(0, 1)

            val error = shouldThrow<IllegalStateException> {
                deliveries.get(
                    messageTo(ShardMaintenanceProcess.TYPE, shardOnly, context = TASKS.toSystem())
                )
            }
            error.message shouldContain "Before upgrading"
        }
    }

    /**
     * Registers an inbox of the tallies, which belongs to the domain context
     * with the given name.
     */
    private fun serveTallies(name: BoundedContextName) {
        deliveries.register(inbox(TALLY_TYPE, name))
    }

    /**
     * Creates an inbox of the given type, which belongs to the given context.
     *
     * The inbox is also registered in the `Delivery` of this test, which the test uses
     * for nothing else.
     */
    private fun inbox(type: TypeUrl, context: BoundedContextName): Inbox<String> =
        delivery()
            .newInbox<String>(type, context)
            .addCommandEndpoint(InboxLabel.HANDLE_COMMAND) { NoOpEndpoint() }
            .build()

    private companion object {

        val TASKS: BoundedContextName = newName("Tasks")
        val BILLING: BoundedContextName = newName("Billing")
        val TALLY_TYPE: TypeUrl = TypeUrl.of(TallyA1::class.java)

        fun job(): CatchUpId = catchUpId {
            uuid = Identifier.newUuid()
            projectionType = TALLY_TYPE.value()
        }

        fun maintenanceOf(job: CatchUpId): ShardMaintenanceId = shardMaintenanceId {
            shard = DeliveryStrategy.newIndex(0, 1)
            this.job = job
        }

        /**
         * Creates a message addressed to the given target of the given type,
         * stored by the inbox of the given context.
         *
         * Only the address and the context of the message are set,
         * as the tested code reads nothing else.
         */
        fun messageTo(
            type: TypeUrl,
            target: Any,
            context: BoundedContextName
        ): @NonValidated InboxMessage =
            InboxMessage.newBuilder()
                .setInboxId(InboxIds.wrap(target, type))
                .setContext(context)
                .buildPartial()
    }
}
