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

package io.spine.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.environment.Tests
import io.spine.server.commandbus.CommandScheduler
import io.spine.server.commandbus.ExecutorCommandScheduler
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.storage.system.SystemAwareStorageFactory
import io.spine.server.transport.memory.InMemoryTransportFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ServerEnvironment` should")
internal class ServerEnvironmentSpec {

    @Test
    fun `be built with the Kotlin DSL, with the technologies for a type of environment`() {
        val storage = InMemoryStorageFactory.newInstance()
        val transport = InMemoryTransportFactory.newInstance()

        val environment = serverEnvironment {
            under<Tests> {
                type shouldBe Tests::class.java
                use(storage)
                use(transport)
            }
        }

        val storageFactory = environment.storageFactory()
        storageFactory.shouldBeInstanceOf<SystemAwareStorageFactory>()
        storageFactory.delegate() shouldBeSameInstanceAs storage
        environment.transportFactory() shouldBeSameInstanceAs transport
    }

    @Test
    fun `use in-memory storage and transport, and a local delivery, in tests by default`() {
        val environment = serverEnvironment {}

        val storageFactory = environment.storageFactory()
        storageFactory.shouldBeInstanceOf<SystemAwareStorageFactory>()
        storageFactory.delegate().shouldBeInstanceOf<InMemoryStorageFactory>()
        environment.transportFactory().shouldBeInstanceOf<InMemoryTransportFactory>()
        environment.delivery() shouldBeSameInstanceAs environment.delivery()
        environment.tracing().isPresent.shouldBeFalse()
    }

    @Test
    fun `create a command scheduler for each call`() {
        val created = mutableListOf<CommandScheduler>()
        val environment = serverEnvironment {
            scheduleCommandsUsing { ExecutorCommandScheduler().also(created::add) }
        }

        environment.newCommandScheduler()
        environment.newCommandScheduler()

        created shouldHaveSize 2
    }

    @Test
    fun `have a node of its own`() {
        val first = serverEnvironment {}
        val second = serverEnvironment {}

        (first.nodeId == second.nodeId).shouldBeFalse()
    }

    @Test
    fun `not be built twice by one builder`() {
        val builder = ServerEnvironment.newBuilder()
        builder.build()

        shouldThrow<IllegalStateException> {
            builder.build()
        }
    }

    @Test
    fun `not be configured after it is built`() {
        lateinit var configurator: ServerEnvironment.TypeConfigurator
        val builder = ServerEnvironment.newBuilder()
            .under<Tests> { configurator = this }
        builder.build()

        shouldThrow<IllegalStateException> {
            configurator.use(InMemoryTransportFactory.newInstance())
        }
    }

    @Test
    fun `close the factories of its technologies`() {
        val storage = InMemoryStorageFactory.newInstance()
        val transport = InMemoryTransportFactory.newInstance()
        val environment = serverEnvironment {
            under<Tests> {
                use(storage)
                use(transport)
            }
        }
        environment.storageFactory()
        environment.transportFactory()

        environment.close()

        environment.isOpen.shouldBeFalse()
        storage.isOpen.shouldBeFalse()
        transport.isOpen.shouldBeFalse()
    }
}
