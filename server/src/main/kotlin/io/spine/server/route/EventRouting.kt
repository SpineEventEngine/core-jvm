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

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.annotation.VisibleForTesting
import io.spine.base.EventMessage
import io.spine.core.EventContext
import io.spine.server.route.EventRoute.Companion.withId
import io.spine.server.route.EventRouting.Companion.withDefault
import io.spine.server.route.EventRouting.Companion.withDefaultByProducerId
import io.spine.system.server.event.EntityStateChanged

/**
 * A [routing schema][MessageRouting] for events.
 *
 * Events are typically delivered to multiple entities,
 * following the [multicast][Multicast] strategy.
 * The `EventRouting` schema contains entries that implement the [EventRoute] interface,
 * a functional type that returns a set of entity identifiers of type [I].
 *
 * Routing functions for events can also follow a [unicast][Unicast] strategy,
 * where an event is routed to a single entity.
 * Such routes can be added using [unicast] functions.
 * These functions adapt their arguments to the [EventRoute] interface.
 *
 * ## Default event route
 *
 * By default, the `EventRouting` schema uses the producer's ID from
 * the [EventContext][EventContext.getProducerId] to determine the route.
 * This behavior is specified when `EventRouting` is [created][withDefaultByProducerId]
 * by a repository.
 *
 * To override the default routing, use [replaceDefault] in
 * the `setupCommandRouting` method of the corresponding repository class.
 * One option for an alternative default route is [EventRoute.byFirstMessageField].
 *
 * ## Adding custom routes
 *
 * Custom routes can be added using [route] functions that accept [EventRoute] implementations.
 * If an event should be routed to a single entity taking the event message or its context,
 * use the [unicast] functions.
 *
 * ## Routing events with a common interface
 *
 * Routing entries can be defined for both specific event classes and interfaces.
 * If you need to route events for both classes and their implemented interfaces,
 * define interface routes *after* entries for specific classes:
 *
 * ```kotlin
 * routing.route<MyEventClass> { event, context -> ... }
 *        .route<AnotherEvent> { event -> ... }
 *        .route<MyInterface> { event, context -> ... }
 *        .route<MySuperInterface> { event -> ... }
 * ```
 *
 * Additionally, define entries for interfaces starting with more specific interfaces before
 * their super-interfaces. If this order is not followed, calling [route] or [unicast] will
 * result in an `IllegalStateException`.
 *
 * @param I The type of entity IDs to which events are routed.
 * @param defaultRoute The fallback route to use when no custom route is provided
 *   via [route] or [invoke].
 */
@Suppress("TooManyFunctions") // We want both inline and Java versions of the methods.
public class EventRouting<I : Any> private constructor(
    defaultRoute: EventRoute<I, EventMessage>
) : MulticastRouting<
        I,
        EventMessage,
        EventContext,
        Set<I>,
        EventRouting<I>
        >(defaultRoute), EventRoute<I, EventMessage> {

    override fun self(): EventRouting<I> = this

    override fun <E : EventMessage> createUnicastRoute(
        via: (E) -> I
    ): EventRoute<I, E> = EventRoute { e, _ -> withId(via(e)) }

    override fun <E : EventMessage> createUnicastRoute(
        via: (E, EventContext) -> I
    ): EventRoute<I, E> = EventRoute { e, c -> withId(via(e, c)) }

    /**
     * Sets a custom routing schema for entity state updates.
     *
     * @param routing The routing schema for entity state updates.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if a route for [EntityStateChanged] is already set.
     */
    @CanIgnoreReturnValue
    public fun routeStateUpdates(routing: StateUpdateRouting<I>): EventRouting<I> =
        route(EntityStateChanged::class.java, routing.eventRoute())

    /**
     * Obtains a route for the passed event class.
     *
     * @param E The type of the event message.
     * @return optionally available route.
     */
    public inline fun <reified E : EventMessage> find(): EventRoute<I, E>? =
        find(E::class.java)

    /**
     * Obtains a route for the passed event class.
     *
     * @param cls The class of the event messages.
     * @param E The type of the event message.
     * @return optionally available route.
     */
    public override fun <E : EventMessage> find(cls: Class<E>): EventRoute<I, E>? =
        super.find(cls) as EventRoute<I, E>?

    public companion object {

        /**
         * Creates a new event routing with the passed default route.
         *
         * @param defaultRoute The default route.
         * @param I The type of entity identifiers returned by the given routing.
         * @return new routing instance.
         */
        @JvmStatic
        @CanIgnoreReturnValue
        @VisibleForTesting
        public fun <I : Any> withDefault(
            defaultRoute: EventRoute<I, EventMessage>
        ): EventRouting<I> = EventRouting(defaultRoute)

        /**
         * Creates a new event routing with the default one by event producer ID.
         *
         * @see withDefault
         * @see EventRoute.byProducerId
         */
        @JvmStatic
        @CanIgnoreReturnValue
        public fun <I : Any> withDefaultByProducerId(): EventRouting<I> =
            EventRouting(EventRoute.byProducerId())
    }
}
