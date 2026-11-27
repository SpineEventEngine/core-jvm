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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.query.RecordQuery
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.server.ContextSpec
import io.spine.server.storage.DelegatingRecordStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AbstractDeliveryTest` should")
internal class AbstractDeliveryTestSpec {

    @Test
    fun `close an application factory with its application`() {
        val created = mutableListOf<PoolFactory>()
        val test = testOver(created)
        test.setUp()
        try {
            val catchUp = test.delivery().catchUpStorage()
            val first = test.blackBoxWith()
            val second = test.blackBoxWith()
            created.shouldHaveSize(3)

            first.close()
            second.close()

            created[0].isOpen.shouldBeTrue()
            created[1].isOpen.shouldBeFalse()
            created[2].isOpen.shouldBeFalse()
            created[1].closings shouldBe 1
            created[2].closings shouldBe 1
            catchUp.clear()

            test.tearDown()

            created[0].closings shouldBe 1
            created[0].isOpen.shouldBeFalse()
            shouldThrow<IllegalStateException> { catchUp.clear() }
        } finally {
            test.tearDown()
        }
        created.forEach { it.closings shouldBe 1 }
    }

    @Test
    fun `close the delivery factory when an application fails to close`() {
        val created = mutableListOf<PoolFactory>()
        val test = testOver(created)
        test.setUp()
        try {
            test.delivery()
            val builder = BoundedContextBuilder.assumingTests()
            builder.setOnBeforeClose { error("The context failed to close.") }
            test.build(builder)

            val error = shouldThrow<IllegalStateException> {
                test.tearDown()
            }

            error.message shouldContain "The context failed to close."
        } finally {
            test.tearDown()
        }
        created.shouldHaveSize(2)
        created.forEach { it.closings shouldBe 1 }
    }

    @Test
    fun `close the application factory when the application fails to build`() {
        val created = mutableListOf<PoolFactory>()
        val test = testOver(created)
        test.setUp()
        try {
            val builder = BoundedContextBuilder.assumingTests()
                .add(FailingRepository())

            val error = shouldThrow<IllegalStateException> {
                test.build(builder)
            }

            error.message shouldContain "The repository failed on registration."
            created[0].isOpen.shouldBeFalse()
            created[0].closings shouldBe 1
        } finally {
            test.tearDown()
        }
        created.forEach { it.closings shouldBe 1 }
    }

    @Test
    fun `close the application factory when the delivery fails to open`() {
        val created = mutableListOf<PoolFactory>()
        val test = object : AbstractDeliveryTest() {
            override fun newStorageFactory(): StorageFactory {
                val factory = PoolFactory(failInbox = created.isNotEmpty())
                created += factory
                return factory
            }
        }
        test.setUp()
        try {
            val error = shouldThrow<IllegalStateException> {
                test.blackBoxWith()
            }

            error.message shouldContain "The inbox storage failed to open."
            created.shouldHaveSize(2)
            created[0].isOpen.shouldBeFalse()
            created[0].closings shouldBe 1
            created[1].isOpen.shouldBeTrue()
        } finally {
            test.tearDown()
        }
        created.forEach { it.closings shouldBe 1 }
    }
}

/**
 * A repository that fails once the context registers it.
 */
private class FailingRepository : CalculatorRepository() {

    override fun onRegistered() {
        super.onRegistered()
        error("The repository failed on registration.")
    }
}

/**
 * Runs [AbstractDeliveryTest] over a new [PoolFactory] on every
 * [AbstractDeliveryTest.newStorageFactory] call.
 */
private fun testOver(created: MutableList<PoolFactory>): AbstractDeliveryTest =
    object : AbstractDeliveryTest() {
        override fun newStorageFactory(): StorageFactory {
            val factory = PoolFactory()
            created += factory
            return factory
        }
    }

/**
 * A storage factory that closes its resources, and whose storages then reject reads.
 *
 * The second call to [close] fails.
 */
private class PoolFactory(private val failInbox: Boolean = false) : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()

    var closings: Int = 0
        private set

    private var open = true

    override fun isOpen(): Boolean = open

    override fun close() {
        check(open) { "The factory was closed more than once." }
        closings += 1
        open = false
        delegate.close()
    }

    override fun createInboxStorage(multiTenant: Boolean): InboxStorage {
        if (failInbox) {
            error("The inbox storage failed to open.")
        }
        return super.createInboxStorage(multiTenant)
    }

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        recordSpec: RecordSpec<I, R>,
        group: StorageGroup?
    ): RecordStorage<I, R> {
        check(open) { "The factory is closed." }
        val storage = delegate.createRecordStorage(context, recordSpec, group)
        return GuardedStorage(context, storage, this)
    }

    /**
     * Throws if this factory is closed.
     */
    fun checkUsable() {
        check(open) { "The factory is closed." }
    }
}

/**
 * A record storage that reads through [factory] and rejects a read once the factory is closed.
 */
private class GuardedStorage<I : Any, R : Message>(
    context: ContextSpec,
    delegate: RecordStorage<I, R>,
    private val factory: PoolFactory
) : DelegatingRecordStorage<I, R>(context, delegate) {

    override fun index(query: RecordQuery<I, R>): MutableIterator<I> {
        factory.checkUsable()
        return super.index(query)
    }
}
