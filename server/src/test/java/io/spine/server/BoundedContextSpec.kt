/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server

import com.example.ForeignClass
import com.example.ForeignContextConfig
import com.google.common.testing.EqualsTester
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.annotation.Internal
import io.spine.base.EventMessage
import io.spine.core.BoundedContextName
import io.spine.core.BoundedContextNames
import io.spine.core.External
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.toJavaLogging
import io.spine.option.EntityOption.Visibility.FULL
import io.spine.server.BoundedContext.multitenant
import io.spine.server.BoundedContext.singleTenant
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.bc.given.FinishedProjectProjection
import io.spine.server.bc.given.ProjectAggregate
import io.spine.server.bc.given.ProjectProcessManager
import io.spine.server.bc.given.ProjectProjection
import io.spine.server.bc.given.ProjectReport
import io.spine.server.bc.given.SecretProjectRepository
import io.spine.server.bc.given.TestEventSubscriber
import io.spine.server.bus.Listener
import io.spine.server.entity.Entity
import io.spine.server.event.EventDispatcher
import io.spine.server.event.Reaction
import io.spine.server.event.React
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.system.server.ConstraintViolated
import io.spine.system.server.SystemClient
import io.spine.system.server.SystemContext
import io.spine.test.bc.Project
import io.spine.test.bc.SecretProject
import io.spine.test.shared.event.SomethingElseHappened
import io.spine.test.shared.event.SomethingHappened
import io.spine.testing.TestValues
import io.spine.testing.logging.Interceptor
import io.spine.testing.server.model.ModelTests
import java.util.function.BooleanSupplier
import java.util.logging.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable

/**
 * Tests of [BoundedContext].
 *
 * Messages used in this test suite are defined in:
 *
 *  * `spine/test/bc/project.proto` — data types
 *  * `spine/test/bc/commands.proto` — commands
 *  * `spine/test/bc/events.proto` — events.
 */
@DisplayName("`BoundedContext` should")
@Suppress("TestFunctionName") // For readability of methods named after class names.
internal class BoundedContextSpec {

    private lateinit var context: BoundedContext

    private val subscriber = TestEventSubscriber()

    private var handlersRegistered = false

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = assumingTests(true).build()
    }

    @AfterEach
    fun tearDown() {
        if (handlersRegistered) {
            context.eventBus().unregister(subscriber)
        }
        if (context.isOpen) {
            context.close()
        }
    }

    /** Registers all test repositories, handlers, etc. */
    private fun registerAll() {
        context.register(DefaultRepository.of(ProjectAggregate::class.java))
        context.eventBus().register(subscriber)
        handlersRegistered = true
    }

    @Nested
    @DisplayName("provide access to")
    internal inner class Return {

        @Test
        fun CommandBus() {
            context.commandBus() shouldNotBe null
        }

        @Test
        fun EventBus() {
            context.eventBus() shouldNotBe null
        }

        @Test
        fun ImportBus() {
            context.importBus() shouldNotBe null
        }

        @Test
        fun Stand() {
            context.stand() shouldNotBe null
        }

        @Test
        fun `multitenancy state`() {
            assumingTests(true).build().use {
                it.isMultitenant shouldBe true
            }
        }

        @Test
        fun SystemClient() {
            context.systemClient() shouldNotBe null
        }
    }

    @Nested
    @DisplayName("provide guarded internal access to")
    inner class InternalAccess {

        private lateinit var access: BoundedContext.InternalAccess

        @BeforeEach
        fun getAccess() {
            access = context.internalAccess()
        }

        @Test
        fun IntegrationBroker() {
            access.broker() shouldNotBe null
        }

        @Test
        fun TenantIndex() {
            access.tenantIndex() shouldNotBe null
        }

        @Test
        fun `prohibiting access from outside of the 'server' package`() {
            assertThrows<SecurityException> {
                ForeignClass.callInternalOf(context)
            }
        }
    }

    @Nested
    @DisplayName("register")
    internal inner class Register {

        @Test
        fun AggregateRepository() =
            registerAndAssertRepository(ProjectAggregate::class.java)

        @Test
        fun ProcessManagerRepository() =
            registerAndAssertRepository(ProjectProcessManager::class.java)

        @Test
        fun ProjectionRepository() =
            registerAndAssertRepository(ProjectReport::class.java)

        /**
         * Registers a [DefaultRepository] for the given entity class and asserts that
         * the context has entities of the given type.
         */
        private fun <I : Any, E : Entity<I, *>> registerAndAssertRepository(cls: Class<out E>) {
            context.register(DefaultRepository.of(cls))
            context.hasEntitiesOfType(cls) shouldBe true
        }

        @Test
        fun `a 'DefaultRepository' via passed entity class`() {
            context.register(ProjectAggregate::class.java)
            context.hasEntitiesOfType<ProjectAggregate>() shouldBe true
        }
    }

    @Nested
    @DisplayName("test presence of entities by")
    internal inner class EntityTypePresence {

        @Nested
        @DisplayName("entity state class for")
        internal inner class ByEntityStateClass {

            @Test
            fun `visible entities`() {
                context.register(ProjectAggregate::class.java)
                context.hasEntitiesWithState<Project>() shouldBe true
            }

            @Test
            @DisplayName("invisible entities")
            fun invisible() {
                context.register(SecretProjectRepository())
                context.hasEntitiesWithState<SecretProject>() shouldBe true
            }
        }

        @Nested
        @DisplayName("entity class for")
        internal inner class ByEntityClass {

            @Test
            fun `visible entities`() {
                context.register(ProjectAggregate::class.java)
                context.hasEntitiesOfType<ProjectAggregate>() shouldBe true
            }

            @Test
            fun `invisible entities`() {
                // Process Managers are invisible by default.
                context.register(ProjectProcessManager::class.java)

                context.hasEntitiesOfType<ProjectProcessManager>() shouldBe true
            }
        }
    }

    @Test
    fun `propagate registered repositories to 'Stand'`() {
        val context = assumingTests().build()
        val stand = context.stand()

        val repo = DefaultRepository.of(ProjectAggregate::class.java)
        val stateType = repo.entityStateType()

        // No type until registration at the context.
        stand.exposedTypes() shouldNotContain stateType

        context.register(repo)

        // After registration, the type is exposed.
        stand.exposedTypes() shouldContain stateType
    }

    @Test
    fun `not allow two entity repositories with entities of same state`() {
        val firstRepo = DefaultRepository.of(FinishedProjectProjection::class.java)
        val secondRepo = DefaultRepository.of(ProjectProjection::class.java)

        context.register(firstRepo)
        assertThrows<IllegalStateException> {
            context.register(secondRepo)
        }
    }

    @Test
    fun `assign storage during registration if repository does not have one`() {
        val repository = DefaultRepository.of(ProjectAggregate::class.java)
        context.register(repository)
        repository.storageAssigned() shouldBe true
    }

    @Nested
    @DisplayName("assign own multitenancy state to")
    internal inner class AssignMultitenancyState {

        private lateinit var context: BoundedContext

        @Test
        fun CommandBus() {
            context = multiTenant()
            assertMultitenancyEqual(
                { context.isMultitenant },
                { context.commandBus().isMultitenant }
            )

            context = singleTenant()
            assertMultitenancyEqual(
                { context.isMultitenant },
                { context.commandBus().isMultitenant }
            )
        }

        @Test
        fun Stand() {
            context = multiTenant()

            assertMultitenancyEqual(
                { context.isMultitenant },
                { context.stand().isMultitenant }
            )

            context = singleTenant()

            assertMultitenancyEqual(
                { context.isMultitenant },
                { context.stand().isMultitenant }
            )
        }

        private fun assertMultitenancyEqual(s1: BooleanSupplier, s2: BooleanSupplier) {
            s1.asBoolean shouldBe s2.asBoolean
        }

        private fun multiTenant(): BoundedContext =
            assumingTests(true).build()

        private fun singleTenant(): BoundedContext =
            assumingTests(false).build()
    }

    /**
     * Simply checks that the result isn't empty to cover the integration with
     * [VisibilityGuard].
     *
     * See [tests of VisibilityGuard][io.spine.server.VisibilityGuard]
     * for how visibility filtering works.
     */
    @Test
    fun `obtain entity types by visibility`() {
        context.stateTypes(FULL).shouldBeEmpty()
        registerAll()
        context.stateTypes(FULL).shouldNotBeEmpty()
    }

    @Test
    fun `throw 'ISE' when obtaining a repository for non-registered entity state class`() {
        // Attempt to get a repository without registering.
        assertThrows<IllegalStateException> {
            context.internalAccess().findRepository(Project::class.java)
        }
    }

    @Test
    fun `not expose invisible aggregates`() {
        ModelTests.dropAllModels()
        context.register(SecretProjectRepository())
        context.internalAccess().findRepository(SecretProject::class.java).shouldBeEmpty()
    }

    @Test
    @DisplayName("prohibit 3rd-party descendants")
    fun `prohibit 3rd-party descendants`() {
        assertThrows<IllegalStateException> {
            object : BoundedContext(assumingTests()) {
                @Internal
                override fun systemClient(): SystemClient = TestValues.nullRef()
            }
        }
    }

    @Nested
    @DisplayName("when closing")
    internal inner class ClosingContext {
        private val contextName: BoundedContextName = BoundedContextNames.newName("TestDomain")
        private val systemContextName: BoundedContextName = contextName.toSystem()
        private val debugLevel: Level = DEBUG.toJavaLogging()

        private lateinit var domainInterceptor: Interceptor
        private lateinit var systemInterceptor: Interceptor

        @BeforeEach
        fun closeContext() {
            val context = singleTenant(contextName.value).build()
            domainInterceptor = Interceptor(DomainContext::class.java, debugLevel)
            domainInterceptor.intercept()
            systemInterceptor = Interceptor(SystemContext::class.java, debugLevel)
            systemInterceptor.intercept()

            context.close()
        }

        @AfterEach
        fun releaseInterceptors() {
            domainInterceptor.release()
            systemInterceptor.release()
        }

        @Test
        fun `log its closing`() {
            domainInterceptor.assertLog().record().run {
                hasLevelThat().isEqualTo(debugLevel)
                hasMessageThat().contains(contextName.value)
            }
        }

        @Test
        fun `close its System context`() {
            systemInterceptor.assertLog().record().run {
                hasLevelThat().isEqualTo(debugLevel)
                hasMessageThat().contains(systemContextName.value)
            }
        }
    }

    @Test
    fun `return its name in 'toString()'`() {
        val name = TestValues.randomString()
        singleTenant(name).build().use {
            it.toString() shouldBe name
        }
    }

    @Nested
    @DisplayName("do not allow registration calls from outside the `io.spine.server` package for")
    internal inner class RestrictRegistrationCalls {

        @Test
        fun Repository() = assertThrowsOn {
            ForeignContextConfig.repositoryRegistration()
        }

        @Test
        fun CommandDispatcher() = assertThrowsOn {
            ForeignContextConfig.commandDispatcherRegistration()
        }

        @Test
        fun EventDispatcher() = assertThrowsOn {
            ForeignContextConfig.eventDispatcherRegistration()
        }

        private fun assertThrowsOn(executable: Executable) {
            Assertions.assertThrows(SecurityException::class.java, executable)
        }
    }

    @Test
    fun `be equal to another context by its name`() {
        val c1 = singleTenant("One").build()
        val c2 = singleTenant("Two").build()
        val c1m = multitenant("One").build()
        val c2m = multitenant("Two").build()
        try {
            EqualsTester()
                .addEqualityGroup(c1, c1m)
                .addEqualityGroup(c2, c2m)
                .testEquals()
        } finally {
            listOf(c1, c2, c1m, c2m).forEach {
                it.close()
            }
        }
    }

    @Test
    fun `be comparable by its name`() {
        val c1 = singleTenant("1").build()
        val c2 = singleTenant("2").build()
        try {
            c1 shouldBeLessThan c2
            c2 shouldBeGreaterThan c1
            multitenant("1").build().use {
                c1 shouldBe it
            }
        } finally {
            listOf(c1, c2).forEach {
                it.close()
            }
        }
    }

    @Nested
    @DisplayName("support diagnostics via `Probe`")
    inner class SupportBlackboxProbe {

        private val probe: BoundedContext.Probe = EmptyProbe()

        @BeforeEach
        fun installProbe() {
            context.install(probe)
        }

        @AfterEach
        fun removeProbe() {
            if (context.hasProbe()) {
                context.removeProbe()
            }
        }

        @Test
        fun installation() {
            context.hasProbe() shouldBe true
            context.probe().shouldBePresent {
                it shouldBe probe
            }
        }

        @Test
        fun removal() {
            context.hasProbe() shouldBe true
            context.removeProbe()
            context.hasProbe() shouldBe false
        }

        @Test
        fun `allowing passing the same probe`() {
            assertDoesNotThrow {
                context.install(probe)
            }
        }

        @Test
        fun `prohibiting setting another probe`() {
            assertThrows<IllegalStateException> {
                context.install(EmptyProbe())
            }
        }

        @Test
        fun `removal when the context closes`() {
            context.close()
            context.hasProbe() shouldBe false
        }
    }

    @Test
    fun `call 'onBeforeClose' when configured`() {
        var called = false
        val context = assumingTests()
            .setOnBeforeClose { called = true }
            .build()
        context.use {
            it.close()
        }
        called shouldBe true
    }
}

private class EmptyProbe : BoundedContext.Probe {
    private lateinit var context: BoundedContext
    override fun registerWith(context: BoundedContext) {
        this.context = context
    }
    override fun isRegistered(): Boolean = this::context.isInitialized
    override fun commandListener(): Listener<CommandEnvelope> = Listener { _ -> }
    override fun eventListener(): Listener<EventEnvelope> = Listener { _ -> }
    override fun eventDispatchers(): Set<EventDispatcher> = mutableSetOf(
            StubReaction1(), StubReaction2(), StubReaction3()
        )
}

private class StubReaction1: Reaction<SomethingHappened>() {
    @React
    override fun whenever(event: SomethingHappened): Iterable<EventMessage> = setOf()
}

private class StubReaction2: Reaction<SomethingElseHappened>() {
    @React
    override fun whenever(event: SomethingElseHappened): Iterable<EventMessage> = setOf()
}

/**
 * A policy which reacts to an external event.
 */
private class StubReaction3: Reaction<ConstraintViolated>() {
    @React
    override fun whenever(@External event: ConstraintViolated): Iterable<EventMessage> = setOf()

}
