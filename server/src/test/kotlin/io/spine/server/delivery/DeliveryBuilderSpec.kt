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

import com.google.protobuf.util.Durations.fromMinutes
import io.kotest.matchers.shouldBe
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.memory.InMemoryShardedWorkRegistry
import io.spine.server.storage.StorageFactory
import io.spine.testing.Assertions.assertIllegalArgument
import io.spine.testing.Assertions.assertNpe
import io.spine.testing.TestValues.nullRef
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`DeliveryBuilder` should")
internal class DeliveryBuilderSpec {

    private fun builder(): DeliveryBuilder = Delivery.newBuilder()

    @Nested
    @DisplayName("not accept `null`")
    internal inner class NotAcceptNull {

        @Test
        fun `delivery strategy`() {
            assertNpe { builder().setStrategy(nullRef()) }
        }

        @Test
        fun `Inbox storage`() {
            assertNpe { builder().setInboxStorage(nullRef()) }
        }

        @Test
        fun `Catch-up storage`() {
            assertNpe { builder().setCatchUpStorage(nullRef()) }
        }

        @Test
        fun `work registry`() {
            assertNpe { builder().setWorkRegistry(nullRef()) }
        }

        @Test
        fun `deduplication window`() {
            assertNpe { builder().setDeduplicationWindow(nullRef()) }
        }

        @Test
        fun `delivery monitor`() {
            assertNpe { builder().setMonitor(nullRef()) }
        }
    }

    @Test
    fun `accept only positive page size`() {
        assertIllegalArgument { builder().setPageSize(0) }
        assertIllegalArgument { builder().setPageSize(-3) }
    }

    @Test
    fun `accept only positive catch-up page size`() {
        assertIllegalArgument { builder().setCatchUpPageSize(0) }
        assertIllegalArgument { builder().setCatchUpPageSize(-3) }
    }

    @Test
    fun `accept only a single-tenant catch-up storage`() {
        val factory = ServerEnvironment.instance().storageFactory()
        val multitenant = CatchUpStorage(factory, /* multitenant = */ true)
        assertIllegalArgument { builder().setCatchUpStorage(multitenant) }
    }

    @Nested
    @DisplayName("return set")
    internal inner class `return set` {

        private lateinit var factory: StorageFactory

        @BeforeEach
        fun createStorageFactory() {
            factory = ServerEnvironment.instance().storageFactory()
        }

        @Test
        fun `delivery strategy`() {
            val strategy = UniformAcrossAllShards.forNumber(42)
            builder().setStrategy(strategy).strategy shouldBe strategy
        }

        @Test
        fun `Inbox storage`() {
            val storage = InboxStorage(factory, false)
            builder().setInboxStorage(storage).inboxStorage shouldBe storage
        }

        @Test
        fun `Catch-up storage`() {
            val storage = CatchUpStorage(factory, false)
            builder().setCatchUpStorage(storage).catchUpStorage shouldBe storage
        }

        @Test
        fun `work registry`() {
            val registry = InMemoryShardedWorkRegistry()
            builder().setWorkRegistry(registry).workRegistry shouldBe registry
        }

        @Test
        fun `deduplication window`() {
            val duration = fromMinutes(123)
            builder().setDeduplicationWindow(duration).deduplicationWindow shouldBe duration
        }

        @Test
        fun `delivery monitor`() {
            val monitor = DeliveryMonitor.alwaysContinue()
            builder().setMonitor(monitor).deliveryMonitor shouldBe monitor
        }

        @Test
        fun `page size`() {
            val pageSize = 42
            builder().setPageSize(pageSize).pageSize shouldBe pageSize
        }

        @Test
        fun `catch-up page size`() {
            val catchUpPageSize = 499
            builder().setCatchUpPageSize(catchUpPageSize).catchUpPageSize shouldBe catchUpPageSize
        }
    }

    @Nested
    @DisplayName("throw `NullPointerException` if attempting to get the unset value of")
    internal inner class ThrowNpe {

        @Test
        fun `delivery strategy`() {
            assertNpe { builder().strategy }
        }

        @Test
        fun `Inbox storage`() {
            assertNpe { builder().inboxStorage }
        }

        @Test
        fun `Catch-up storage`() {
            assertNpe { builder().catchUpStorage }
        }

        @Test
        fun `work registry`() {
            assertNpe { builder().workRegistry }
        }

        @Test
        fun `deduplication window`() {
            assertNpe { builder().deduplicationWindow }
        }

        @Test
        fun `delivery monitor`() {
            assertNpe { builder().deliveryMonitor }
        }

        @Test
        fun `page size`() {
            assertNpe { builder().pageSize }
        }
    }
}
