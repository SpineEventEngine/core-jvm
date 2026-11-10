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

import com.google.protobuf.Message
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.base.CommandMessage
import io.spine.core.BoundedContextName
import io.spine.environment.Tests
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.commandbus.CommandScheduler
import io.spine.server.commandbus.ExecutorCommandScheduler
import io.spine.server.delivery.Delivery
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.trace.given.MemoizingTracerFactory
import io.spine.server.trace.given.TracingTestEnv.FLIGHT
import io.spine.server.trace.given.TracingTestEnv.FLIGHT_TYPE
import io.spine.server.trace.given.TracingTestEnv.scheduleFlight
import io.spine.server.trace.given.airport.AirportContext
import io.spine.server.transport.memory.InMemoryTransportFactory
import io.spine.test.trace.ScheduleFlight
import io.spine.testing.Assertions.assertNpe
import io.spine.testing.TestValues.nullRef
import io.spine.testing.client.TestActorRequestFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.Supplier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError

/**
 * Checks the technologies of the context, which [BoundedContextBuilder] passes to
 * the built context.
 *
 * Supplements `BoundedContextBuilderTest`, which checks the other properties of the builder.
 */
@DisplayName("`BoundedContextBuilder` should")
internal class BoundedContextBuilderSpec {

    private val requests = TestActorRequestFactory(BoundedContextBuilderSpec::class.java)
    private var context: BoundedContext? = null
    private var environmentChanged = false

    /**
     * Closes the built context, and then resets the server environment if the test changed it.
     */
    @AfterEach
    fun closeContext() {
        try {
            context?.close()
        } finally {
            if (environmentChanged) {
                ServerEnvironment.instance().reset()
            }
        }
    }

    @Nested inner class
    `have a tracer factory, which` {

        @Test
        fun `is not set by default`() {
            AirportContext.builder().tracerFactory shouldBe null
        }

        @Test
        fun `is returned once set`() {
            val factory = MemoizingTracerFactory()
            val builder = AirportContext.builder().setTracerFactory(factory)

            builder.tracerFactory shouldBe factory
        }

        @Test
        fun `cannot be 'null'`() {
            assertNpe { AirportContext.builder().setTracerFactory(nullRef()) }
        }

        @Test
        fun `traces the messages of the built context`() {
            val tracing = MemoizingTracerFactory()
            val built = buildWith(tracing)

            post(built, scheduleFlight())

            tracing.tracer(built.spec(), ScheduleFlight::class.java)
                .isReceiver(FLIGHT, FLIGHT_TYPE)
                .shouldBeTrue()
        }

        @Test
        fun `is used instead of the one of the server environment`() {
            val ofEnvironment = MemoizingTracerFactory()
            environmentChanged = true
            under<Tests> { use(ofEnvironment) }
            val tracing = MemoizingTracerFactory()
            val built = buildWith(tracing)

            post(built, scheduleFlight())

            tracing.tracer(built.spec(), ScheduleFlight::class.java)
                .isReceiver(FLIGHT, FLIGHT_TYPE)
                .shouldBeTrue()
            // The factory fails the assertion if it traced no message of the type.
            shouldThrow<AssertionFailedError> {
                ofEnvironment.tracer(built.spec(), ScheduleFlight::class.java)
            }
        }
    }

    @Nested inner class
    `have the technologies of the context, which` {

        @Test
        fun `are not set by default`() {
            val builder = BoundedContextBuilder.assumingTests()

            builder.storageFactory shouldBe null
            builder.delivery shouldBe null
            builder.commandSchedulerFactory shouldBe null
            builder.transportFactory shouldBe null
        }

        @Test
        fun `cannot be 'null'`() {
            val builder = BoundedContextBuilder.assumingTests()

            assertNpe { builder.setStorageFactory(nullRef()) }
            assertNpe { builder.setDelivery(nullRef()) }
            assertNpe { builder.setCommandSchedulerFactory(nullRef()) }
            assertNpe { builder.setTransportFactory(nullRef()) }
        }

        @Test
        fun `are used by the built context once set`() {
            val storageFactory = InMemoryStorageFactory.newInstance()
            val delivery = Delivery.local(ServerEnvironment.instance())
            val schedulers = RecordingSchedulerFactory()
            val transportFactory = InMemoryTransportFactory.newInstance()
            val builder = BoundedContextBuilder.assumingTests()
                .setStorageFactory(storageFactory)
                .setDelivery(delivery)
                .setCommandSchedulerFactory(schedulers)
                .setTransportFactory(transportFactory)

            val access = build(builder).internalAccess()

            access.storageFactory() shouldBeSameInstanceAs storageFactory
            access.delivery() shouldBeSameInstanceAs delivery
            schedulers.created shouldContain access.commandScheduler()
            access.transportFactory() shouldBeSameInstanceAs transportFactory
            transportFactory.close()
        }

        @Test
        fun `are taken from the server environment, if not set`() {
            val builder = BoundedContextBuilder.assumingTests()

            val access = build(builder).internalAccess()

            val environment = ServerEnvironment.instance()
            access.storageFactory() shouldBeSameInstanceAs environment.storageFactory()
            access.delivery() shouldBeSameInstanceAs environment.delivery()
            access.transportFactory() shouldBeSameInstanceAs environment.transportFactory()
            access.commandScheduler().shouldNotBeNull()
            builder.storageFactory shouldBeSameInstanceAs environment.storageFactory()
            builder.commandSchedulerFactory.shouldNotBeNull()
        }

        @Test
        fun `are shared with the System context`() {
            val storageFactory = RecordingStorageFactory()
            val builder = BoundedContextBuilder.assumingTests()
                .setStorageFactory(storageFactory)

            val built = build(builder)

            storageFactory.contexts shouldContainAll
                    listOf(built.name(), built.spec().toSystem().name())
        }

        @Test
        fun `give each context its own command scheduler`() {
            val schedulers = RecordingSchedulerFactory()
            val builder = BoundedContextBuilder.assumingTests()
                .setCommandSchedulerFactory(schedulers)

            val built = build(builder)

            // One scheduler is for the built context, the other is for its System context.
            schedulers.created shouldHaveSize 2
            schedulers.created.toSet() shouldHaveSize 2
            schedulers.created shouldContain built.internalAccess().commandScheduler()
        }
    }

    private fun buildWith(tracing: MemoizingTracerFactory): BoundedContext =
        build(AirportContext.builder().setTracerFactory(tracing))

    private fun build(builder: BoundedContextBuilder): BoundedContext {
        val built = builder.build()
        context = built
        return built
    }

    private fun post(context: BoundedContext, command: CommandMessage) {
        val cmd = requests.command().create(command)
        context.commandBus().post(cmd, noOpObserver())
    }
}

/**
 * Creates in-memory storages, recording the names of the contexts it creates them for.
 */
private class RecordingStorageFactory : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()
    val contexts = CopyOnWriteArraySet<BoundedContextName>()

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        recordSpec: RecordSpec<I, R>,
        group: StorageGroup?
    ): RecordStorage<I, R> {
        contexts.add(context.name())
        return delegate.createRecordStorage(context, recordSpec, group)
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}

/**
 * Creates a new executor-based command scheduler on each call, remembering each of them.
 */
private class RecordingSchedulerFactory : Supplier<CommandScheduler> {

    val created = CopyOnWriteArrayList<CommandScheduler>()

    override fun get(): CommandScheduler =
        ExecutorCommandScheduler().also { created.add(it) }
}
