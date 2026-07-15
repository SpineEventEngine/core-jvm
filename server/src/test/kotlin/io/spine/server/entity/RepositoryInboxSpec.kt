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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.Inbox
import io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER
import io.spine.server.entity.given.repository.ProjectEntity
import io.spine.test.entity.Project
import io.spine.test.entity.ProjectId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the `Inbox` and `RepositoryCache` lifecycle owned by [Repository].
 *
 * Both resources are created by `registerWith()` only for a repository that
 * [configures][Repository.setupInbox] at least one inbox endpoint.
 */
@DisplayName("`Repository`, as the owner of the `Inbox` and the cache, should")
internal class RepositoryInboxSpec {

    private val context = BoundedContextBuilder.assumingTests().build()

    @Test
    fun `create neither an inbox nor a cache for a repository configuring no endpoints`() {
        val repo = NoEndpointsRepository()
        context.internalAccess().register(repo)

        shouldThrow<IllegalStateException> { repo.exposedInbox() }
        shouldThrow<IllegalStateException> { repo.exposedCache() }
    }

    @Test
    fun `create both for a repository configuring an endpoint`() {
        val repo = WithEndpointRepository()
        context.internalAccess().register(repo)

        repo.exposedInbox() shouldNotBe null
        repo.exposedCache() shouldNotBe null
    }

    @Test
    fun `not fail the registration of a repository dispatching no messages by default`() {
        // `NoEndpointsRepository` does not override `checkDispatchesMessages()`,
        // so the no-op default of `Repository` applies.
        context.internalAccess().register(NoEndpointsRepository())
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
