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

import com.google.protobuf.Message
import com.google.protobuf.util.Durations.fromMinutes
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.server.ContextSpec
import io.spine.server.NodeId
import io.spine.server.delivery.memory.InMemoryShardedWorkRegistry
import io.spine.server.nodeId
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.testing.Assertions.assertIllegalArgument
import io.spine.testing.Assertions.assertNpe
import io.spine.testing.TestValues.nullRef
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`DeliveryBuilder` should")
internal class DeliveryBuilderSpec {

    private val node: NodeId = nodeId { value = "DeliveryBuilderSpec" }

    private fun builder(): DeliveryBuilder = Delivery.newBuilder(node, null)

    @Nested
    @DisplayName("not accept `null`")
    internal inner class NotAcceptNull {

        @Test
        fun `node ID`() {
            assertNpe { Delivery.newBuilder(nullRef(), null) }
        }

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
        val factory = InMemoryStorageFactory.newInstance()
        val multiTenant = CatchUpStorage(factory, /* multiTenant = */ true)
        assertIllegalArgument { builder().setCatchUpStorage(multiTenant) }
    }

    @Nested internal inner class
    `create the storages that are not set` {

        @Test
        fun `with the given storage factory`() {
            val factory = CountingStorageFactory()

            Delivery.newBuilder(node, factory).build()

            factory.inboxStorages shouldBe 1
            factory.catchUpStorages shouldBe 1
        }

        @Test
        fun `with a new in-memory storage factory, if none is given`() {
            val builder = Delivery.newBuilder(node, null)

            builder.build()

            builder.inboxStorage.shouldNotBeNull()
            builder.catchUpStorage.shouldNotBeNull()
        }

        @Test
        fun `but not the ones that are set`() {
            val factory = CountingStorageFactory()
            val storages = InMemoryStorageFactory.newInstance()

            Delivery.newBuilder(node, factory)
                .setInboxStorage(storages.createInboxStorage(/* multiTenant = */ false))
                .setCatchUpStorage(storages.createCatchUpStorage(/* multiTenant = */ false))
                .build()

            factory.inboxStorages shouldBe 0
            factory.catchUpStorages shouldBe 0
        }
    }

    @Nested
    @DisplayName("return set")
    internal inner class `return set` {

        private lateinit var factory: StorageFactory

        @BeforeEach
        fun createStorageFactory() {
            factory = InMemoryStorageFactory.newInstance()
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

    @Nested internal inner class
    `tell if it has` {

        private lateinit var factory: StorageFactory

        @BeforeEach
        fun createStorageFactory() {
            factory = InMemoryStorageFactory.newInstance()
        }

        @Test
        fun `delivery strategy`() {
            val builder = builder()
            builder.hasStrategy() shouldBe false
            builder.setStrategy(UniformAcrossAllShards.singleShard())
            builder.hasStrategy() shouldBe true
        }

        @Test
        fun `Inbox storage`() {
            val builder = builder()
            builder.hasInboxStorage() shouldBe false
            builder.setInboxStorage(InboxStorage(factory, /* multiTenant = */ false))
            builder.hasInboxStorage() shouldBe true
        }

        @Test
        fun `Catch-up storage`() {
            val builder = builder()
            builder.hasCatchUpStorage() shouldBe false
            builder.setCatchUpStorage(CatchUpStorage(factory, /* multiTenant = */ false))
            builder.hasCatchUpStorage() shouldBe true
        }

        @Test
        fun `work registry`() {
            val builder = builder()
            builder.hasWorkRegistry() shouldBe false
            builder.setWorkRegistry(InMemoryShardedWorkRegistry())
            builder.hasWorkRegistry() shouldBe true
        }

        @Test
        fun `deduplication window`() {
            val builder = builder()
            builder.hasDeduplicationWindow() shouldBe false
            builder.setDeduplicationWindow(fromMinutes(1))
            builder.hasDeduplicationWindow() shouldBe true
        }

        @Test
        fun `delivery monitor`() {
            val builder = builder()
            builder.hasDeliveryMonitor() shouldBe false
            builder.setMonitor(DeliveryMonitor.alwaysContinue())
            builder.hasDeliveryMonitor() shouldBe true
        }

        @Test
        fun `page size`() {
            val builder = builder()
            builder.hasPageSize() shouldBe false
            builder.setPageSize(42)
            builder.hasPageSize() shouldBe true
        }

        @Test
        fun `catch-up page size`() {
            val builder = builder()
            builder.hasCatchUpPageSize() shouldBe false
            builder.setCatchUpPageSize(42)
            builder.hasCatchUpPageSize() shouldBe true
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

        @Test
        fun `catch-up page size`() {
            assertNpe { builder().catchUpPageSize }
        }
    }
}

/**
 * Creates in-memory storages, counting the inbox and catch-up storages it creates.
 */
private class CountingStorageFactory : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()

    var inboxStorages = 0
        private set

    var catchUpStorages = 0
        private set

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        recordSpec: RecordSpec<I, R>,
        group: StorageGroup?
    ): RecordStorage<I, R> = delegate.createRecordStorage(context, recordSpec, group)

    override fun createInboxStorage(multiTenant: Boolean): InboxStorage {
        inboxStorages++
        return delegate.createInboxStorage(multiTenant)
    }

    override fun createCatchUpStorage(multiTenant: Boolean): CatchUpStorage {
        catchUpStorages++
        return delegate.createCatchUpStorage(multiTenant)
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}
