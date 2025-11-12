/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.given.context.users

import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import io.spine.core.Subscribe
import io.spine.core.UserId
import io.spine.server.BoundedContext.singleTenant
import io.spine.server.BoundedContextBuilder
import io.spine.server.aggregate.Aggregate
import io.spine.server.aggregate.AggregateRepository
import io.spine.server.aggregate.Apply
import io.spine.server.entity.alter
import io.spine.server.event.React
import io.spine.server.given.context.users.event.RUserConsentRequested
import io.spine.server.given.context.users.event.RUserSignedIn
import io.spine.server.given.context.users.event.rUserConsentRequested
import io.spine.server.projection.Projection
import io.spine.server.projection.ProjectionRepository
import io.spine.server.route.EventRouting

/**
 * This file provides Users context for testing event routing order.
 */
@Suppress("unused")
private const val ABOUT = ""

fun createUsersContext(): BoundedContextBuilder = singleTenant("Users").apply {
    add(UserRepository())
    add(SessionRepository())
}

/**
 * Remembers the user's consent in response to [RUserConsentRequested]
 */
class SessionProjection : Projection<RSessionId, RSession, RSession.Builder>() {

    @Subscribe
    internal fun on(event: RUserSignedIn) {
        builder().setId(event.session).userId = event.user
    }

    @Subscribe
    internal fun on(@Suppress("UNUSED_PARAMETER") e: RUserConsentRequested) = alter {
        userConsentRequested = true
    }
}

private class SessionRepository : ProjectionRepository<RSessionId, SessionProjection, RSession>() {

    @OverridingMethodsMustInvokeSuper
    override fun setupEventRouting(routing: EventRouting<RSessionId>) {
        super.setupEventRouting(routing)
        routing
            .unicast(RUserSignedIn::class.java) { e -> e.session }
            .route(RUserConsentRequested::class.java) { e, _ -> findByUserId(e.user) }
    }

    private fun findByUserId(id: UserId): Set<RSessionId> {
        val query = RSession.query()
            .userId().`is`(id)
            .build()
        val identifiers = recordStorage().index(query)
        return ImmutableSet.copyOf(identifiers)
    }
}

private class UserAggregate : Aggregate<UserId, RUser, RUser.Builder>() {

    @React
    fun on(event: RUserSignedIn): RUserConsentRequested = rUserConsentRequested {
        user = event.user
    }

    @Apply
    private fun on(@Suppress("UNUSED_PARAMETER") event: RUserConsentRequested) = alter {
        userConsentRequested = true
    }
}

private class UserRepository : AggregateRepository<UserId, UserAggregate, RUser>() {

    override fun setupEventRouting(routing: EventRouting<UserId>) {
        super.setupEventRouting(routing)
        routing
            .unicast(RUserSignedIn::class.java) { e -> e.user }
            .unicast(RUserConsentRequested::class.java) { e -> e.user }
    }
}
