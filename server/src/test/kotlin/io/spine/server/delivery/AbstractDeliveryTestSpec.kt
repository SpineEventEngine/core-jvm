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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.server.BoundedContextBuilder
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.memory.InMemoryStorageFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AbstractDeliveryTest` should")
internal class AbstractDeliveryTestSpec {

    @Test
    fun `close the shared storage factory once, after the black boxes`() {
        val factory = RecordedFactory()
        val test = testOver(factory)
        test.setUp()
        try {
            val first = test.blackBoxWith()
            val second = test.blackBoxWith()
            first.close()
            second.close()

            factory.isOpen.shouldBeTrue()
            factory.closings shouldBe 0
            test.delivery().catchUpStorage().clear()

            test.tearDown()

            factory.closings shouldBe 1
            factory.isOpen.shouldBeFalse()

            test.tearDown()
            factory.closings shouldBe 1
        } finally {
            test.tearDown()
        }
    }

    @Test
    fun `close the storage factory that the delivery opened`() {
        val factory = RecordedFactory()
        val test = testOver(factory)
        test.setUp()
        try {
            test.delivery()

            factory.closings shouldBe 0

            test.tearDown()

            factory.closings shouldBe 1
            factory.isOpen.shouldBeFalse()
        } finally {
            test.tearDown()
        }
    }

    @Test
    fun `close the storage factory when an application fails to close`() {
        val factory = RecordedFactory()
        val test = testOver(factory)
        test.setUp()
        try {
            val builder = BoundedContextBuilder.assumingTests()
            builder.setOnBeforeClose { error("The context failed to close.") }
            test.build(builder)

            val error = shouldThrow<IllegalStateException> {
                test.tearDown()
            }

            error.message shouldContain "The context failed to close."
            factory.closings shouldBe 1
            factory.isOpen.shouldBeFalse()
        } finally {
            test.tearDown()
        }
        factory.closings shouldBe 1
    }
}

/**
 * Runs [AbstractDeliveryTest] over the given factory.
 */
private fun testOver(factory: StorageFactory): AbstractDeliveryTest =
    object : AbstractDeliveryTest() {
        override fun storageFactory(): StorageFactory = factory
    }

/**
 * An in-memory storage factory that counts the calls to [close].
 *
 * The second call fails, so a suite that closes the factory twice fails the test.
 */
private class RecordedFactory(
    private val delegate: StorageFactory = InMemoryStorageFactory.newInstance()
) : StorageFactory by delegate {

    var closings: Int = 0
        private set

    override fun close() {
        closings += 1
        check(closings == 1) { "The storage factory was closed $closings times." }
        delegate.close()
    }
}
