/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity

import com.google.common.collect.ImmutableSet
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.core.Versions
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.Inbox
import io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.given.repository.ProjectEntity
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.test.entity.Project
import io.spine.test.entity.ProjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the `Inbox` and `RepositoryCache` lifecycle owned by [Repository].
 *
 * Every registered repository has a cache. Only the inbox is optional — a repository
 * gets one just when it [configures][Repository.setupInbox] at least one endpoint.
 */
@DisplayName("`Repository`, as the owner of the `Inbox` and the cache, should")
internal class RepositoryInboxAndCacheSpec {

    private val context = BoundedContextBuilder.assumingTests().build()

    /**
     * Closes the context, which closes the repositories registered with it.
     *
     * Closing a repository unregisters its inbox from the `Delivery` of the current
     * `ServerEnvironment`, which is server-wide and outlives this test.
     */
    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    fun `create a cache, but no inbox, for a repository configuring no endpoints`() {
        val repo = NoEndpointsRepository()
        context.internalAccess().register(repo)

        shouldNotThrowAny { repo.exposedCache() }

        val error = shouldThrow<IllegalStateException> { repo.exposedInbox() }
        error.message shouldContain "has no `Inbox`"
    }

    @Test
    fun `create both for a repository configuring an endpoint`() {
        val repo = WithEndpointRepository()
        context.internalAccess().register(repo)

        shouldNotThrowAny { repo.exposedInbox() }
        shouldNotThrowAny { repo.exposedCache() }
    }

    @Test
    fun `treat an absent cache as an initialization error`() {
        // `registerWith()` is what creates the cache, and this repository is not registered.
        val error = shouldThrow<IllegalStateException> { NoEndpointsRepository().exposedCache() }
        error.message shouldContain "has no cache"
    }

    @Test
    fun `not fail the registration of a repository dispatching no messages by default`() {
        // `NoEndpointsRepository` does not override `checkDispatchesMessages()`,
        // so the no-op default of `Repository` applies.
        context.internalAccess().register(NoEndpointsRepository())
    }

    @Test
    fun `store and load through the cache of an event-dispatching repository with no inbox`() {
        val repo = NoInboxDispatchingRepository()
        context.internalAccess().register(repo)

        val id = ProjectId.newBuilder()
            .setId("no-inbox")
            .build()
        val entity = repo.create(id)
        val state = entity.state()
            .toBuilder()
            .setId(id)
            .build()
        TestTransaction.injectState(entity, state, Versions.zero())

        repo.store(entity)

        repo.find(id).isPresent shouldBe true
        repo.loadOrCreate(id).state() shouldBe state
    }
}

/**
 * A repository that leaves the [Inbox.Builder] untouched, and so gets no inbox.
 *
 * This is the shape of every plain record-based repository.
 */
private open class NoEndpointsRepository
    : DefaultRecordBasedRepository<ProjectId, ProjectEntity, Project>() {

    /** Exposes the `protected` accessor to this test. */
    fun exposedInbox(): Inbox<ProjectId> = inbox()

    /** Exposes the `protected` accessor to this test. */
    fun exposedCache(): RepositoryCache<ProjectId, ProjectEntity> = cache()
}

/**
 * A repository that adds an endpoint, and so gets both an inbox and a cache.
 */
private class WithEndpointRepository : NoEndpointsRepository() {

    override fun setupInbox(builder: Inbox.Builder<ProjectId>) {
        builder.addEventEndpoint(UPDATE_SUBSCRIBER) {
            error("This endpoint is never reached in this test.")
        }
    }
}

/**
 * An [EventDispatchingRepository] that adds no inbox endpoints, and so has a cache
 * but no inbox.
 *
 * `ProcessManagerRepository` and `ProjectionRepository` — the only two subclasses in the
 * framework — both add endpoints unconditionally, so this stub is the only way to check
 * that [Repository.store] and [Repository.findOrCreate] serve an inbox-less repository
 * through its cache.
 */
private class NoInboxDispatchingRepository
    : EventDispatchingRepository<ProjectId, ProjectEntity, Project>() {

    override fun messageClasses(): ImmutableSet<EventClass> = ImmutableSet.of()

    override fun domesticEventClasses(): ImmutableSet<EventClass> = ImmutableSet.of()

    override fun externalEventClasses(): ImmutableSet<EventClass> = ImmutableSet.of()

    override fun dispatchTo(ids: Set<ProjectId>, event: EventEnvelope): DispatchOutcome =
        error("This repository dispatches nothing in this test.")

    /** Exposes the `protected` accessor to this test. */
    fun loadOrCreate(id: ProjectId): ProjectEntity = findOrCreate(id)
}
