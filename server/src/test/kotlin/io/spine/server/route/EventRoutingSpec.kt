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
package io.spine.server.route

import com.google.common.collect.ImmutableSet
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.base.EventMessage
import io.spine.core.EventContext
import io.spine.server.route.EventRouting.Companion.withDefault
import io.spine.server.type.EventEnvelope
import io.spine.server.type.given.GivenEvent
import io.spine.test.route.AccountSuspended
import io.spine.test.route.LoginEvent
import io.spine.test.route.UserAccountEvent
import io.spine.test.route.UserEvent
import io.spine.test.route.UserLoggedIn
import io.spine.test.route.UserLoggedOut
import io.spine.test.route.UserRegistered
import io.spine.testing.TestValues
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`EventRouting` should")
internal class EventRoutingSpec {

    /** The object under the test.  */
    private lateinit var eventRouting: EventRouting<Long>

    /** The default route to be used by the routing under the test.  */
    private val defaultRoute =
        EventRoute { _: EventMessage, _: EventContext -> DEFAULT_ROUTE }

    /** The route for all user events. */
    private val userEventRoute =
        EventRoute { _: UserEvent, _: EventContext ->
            ImmutableSet.of(
                -5L,
                -6L,
                -7L
            )
        }

    /** The route for all user account events. */
    private val userAccountEventRoute =
        EventRoute { _: UserAccountEvent, _: EventContext ->
            ImmutableSet.of(
                0L,
                -3L,
                -10L
            )
        }

    /** A type-specific route.  */
    private val userRegisteredRoute =
        EventRoute { _: UserRegistered, _: EventContext -> USER_REGISTERED_ROUTE }

    /** Another type-specific route. */
    private val accountSuspendedRoute =
        EventRoute { _: AccountSuspended, _: EventContext ->
            ImmutableSet.of(
                100L,
                200L
            )
        }

    /** An interface-based route. */
    private val loginEventRoute =
        EventRoute { _: LoginEvent, _: EventContext -> LOGIN_EVENT_ROUTE }

    /** Another custom route.  */
    private val userLoggedOutRoute =
        EventRoute { _: UserLoggedOut, _: EventContext -> LOGGED_OUT_ROUTE }

    @BeforeEach
    fun setUp() {
        eventRouting = withDefault<Long>(defaultRoute)
    }

    @Test
    fun `have default route`() {
        eventRouting.defaultRoute() shouldNotBe null
    }

    @Test
    fun `allow replacing default route`() {
        val newDefault =
            EventRoute { _: EventMessage, _: EventContext ->
                ImmutableSet.of(
                    10L,
                    20L
                )
            }

        eventRouting.replaceDefault(newDefault) shouldBeSameInstanceAs eventRouting
        eventRouting.defaultRoute() shouldBeSameInstanceAs newDefault
    }

    @Test
    fun `set custom route`() {
        eventRouting.route<UserRegistered>(userRegisteredRoute) shouldBeSameInstanceAs eventRouting

        val route = eventRouting.find<UserRegistered>()

        route shouldBe userRegisteredRoute
    }

    @Test
    fun `not allow overwriting set route`() {
        eventRouting.route<UserRegistered>(userRegisteredRoute)
        assertThrows<IllegalStateException>{
            eventRouting.route<UserRegistered>(userRegisteredRoute)
        }
    }

    @Test
    fun `remove previously set route`() {
        eventRouting.route<UserRegistered>(userRegisteredRoute)
        eventRouting.remove<UserRegistered>()

        eventRouting.routeFor(UserLoggedIn::class.java)
            .found shouldBe false
    }

    @Test
    fun `throw 'ISE' on removal if route is not set`() {
        assertThrows<IllegalStateException> {
            eventRouting.remove<UserRegistered>()
        }
    }

    @Test
    fun `apply default route`() {
        // Have a custom route too.
        eventRouting.route(UserRegistered::class.java, userRegisteredRoute)

        val event = GivenEvent.arbitrary()
        val ids = eventRouting(event.enclosedMessage(), event.context())

        ids shouldBe DEFAULT_ROUTE
    }

    @Test
    fun `apply custom route`() {
        eventRouting.route(UserRegistered::class.java, userRegisteredRoute)

        val eventMessage = UserRegistered.newBuilder()
            .setId(TestValues.random(1, 100).toLong())
            .build()
        val event = EventEnvelope.of(GivenEvent.withMessage(eventMessage))

        val ids = eventRouting(event.message(), event.context())

        ids shouldBe USER_REGISTERED_ROUTE
    }

    @Test
    fun `allow routing via interface`() {
        eventRouting.route(UserLoggedOut::class.java, userLoggedOutRoute)
            .route(LoginEvent::class.java, loginEventRoute)

        val ctx = EventContext.getDefaultInstance()

        // Check routing via common interface `LoginEvent`.
        eventRouting(UserLoggedIn.getDefaultInstance(), ctx) shouldBe LOGIN_EVENT_ROUTE

        // Check routing via a specific type.
        eventRouting(UserLoggedOut.getDefaultInstance(), ctx) shouldBe LOGGED_OUT_ROUTE
    }

    @Test
    fun `prohibit adding specific type after interface-based route`() {
        assertThrows<IllegalStateException> {
            eventRouting.route<UserEvent>(userEventRoute)
                        .route<AccountSuspended>(accountSuspendedRoute)
        }
    }

    @Test
    fun `cache routing defined via interface`() {
        eventRouting.route<LoginEvent>(loginEventRoute)

        // This is the first time we try to find the route.
        val firstMatch = eventRouting.routeFor(UserLoggedIn::class.java)

        // It should be matched to the interface type defined in `eventRouting` above.
        firstMatch.found shouldBe true
        firstMatch.entryClass shouldBe LoginEvent::class.java

        // On the second attempt, we should see the entry for the exact class.
        val secondMatch = eventRouting.routeFor(UserLoggedIn::class.java)

        secondMatch.found shouldBe true
        secondMatch.entryClass shouldBe UserLoggedIn::class.java
    }

    @Test
    fun `use default route when neither direct nor interface routing is defined`() {
        eventRouting.route(UserAccountEvent::class.java, userAccountEventRoute)

        // Obtain a match for the type from another “branch” of events.
        val match = eventRouting.routeFor(UserLoggedIn::class.java)

        match.found shouldBe false

        val route = eventRouting(
            UserLoggedIn.getDefaultInstance(),
            EventContext.getDefaultInstance()
        )
        route shouldBe DEFAULT_ROUTE
    }

    companion object {
        /** The set of IDs returned by [defaultRoute].  */
        private val DEFAULT_ROUTE: ImmutableSet<Long> = ImmutableSet.of(0L, 1L)

        /** The set of IDs returned by [loginEventRoute].  */
        private val LOGIN_EVENT_ROUTE: ImmutableSet<Long> = ImmutableSet.of(-1L, -2L, -3L)

        /** The set of IDs returned by [userRegisteredRoute].  */
        private val USER_REGISTERED_ROUTE: ImmutableSet<Long> = ImmutableSet.of(5L, 6L, 7L)

        /** The set of IDs returned by the [userLoggedOutRoute].  */
        private val LOGGED_OUT_ROUTE: ImmutableSet<Long> = ImmutableSet.of(100L, 200L, 300L, 400L)
    }
}
