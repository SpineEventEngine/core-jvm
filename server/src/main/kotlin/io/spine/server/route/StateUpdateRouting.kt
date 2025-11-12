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

import io.spine.base.EntityState
import io.spine.core.EventContext
import io.spine.protobuf.AnyPacker
import io.spine.system.server.event.EntityStateChanged

/**
 * A [routing schema][MessageRouting] designed for delivering entity state updates.
 *
 * ## Purpose of routing entity states
 *
 * Entities sometimes require updates based on the state of other entities. For instance,
 * a summary report might need to aggregate information from several detailed reports.
 * While subscribing to the same events as those used for detailed reports is an option,
 * it often results in duplicated or near-identical code.
 *
 * A more efficient approach is to [subscribe][io.spine.core.Subscribe] to messages
 * that are subclasses of [EntityState].
 *
 * ## Default routing behavior
 *
 * By default, the entity state route selects the value of the first field whose type matches
 * the type parameter [I] of the target entities. This behavior can be customized by using the
 * [replaceDefault] function.
 *
 * ## Defining custom routes
 *
 * Like [EventRouting], entity state routes support [multicast][Multicast] delivery.
 * Custom routes that deliver an entity state to multiple entities can be defined using the
 * [route] functions, which accept implementations of the [StateUpdateRoute] functional interface.
 *
 * For cases where an entity state should be routed to a single target, use the [unicast] functions.
 *
 * @param I The type of entity IDs to which updates are routed.
 * @param idClass The class of entity identifiers to which entity states are routed.
 */
public class StateUpdateRouting<I : Any> private constructor(
    idClass: Class<I>
) : MulticastRouting<
        I,
        EntityState<*>,
        EventContext, Set<I>,
        StateUpdateRouting<I>
        >(
    DefaultStateRoute.newInstance(idClass)
) {
    override fun self(): StateUpdateRouting<I> = this

    override fun <E : EntityState<*>> createUnicastRoute(
        via: (E, EventContext) -> I
    ): StateUpdateRoute<I, E> = StateUpdateRoute { state, context -> setOf(via(state, context)) }

    override fun <E : EntityState<*>> createUnicastRoute(
        via: (E) -> I
    ): StateUpdateRoute<I, E> = StateUpdateRoute { state, _ -> setOf(via(state)) }

    /**
     * Verifies if the passed state type can be routed by a custom route, or
     * the message has a field matching the type of identifiers served by this routing.
     */
    override fun supports(messageType: Class<out EntityState<*>>): Boolean {
        val customRouteSet = super.supports(messageType)
        val defaultRoute = defaultRoute() as DefaultStateRoute<I>
        val defaultRouteAvailable = defaultRoute.supports(messageType)
        return customRouteSet || defaultRouteAvailable
    }

    /**
     * Creates an [EventRoute] for [EntityStateChanged] events based on this routing.
     *
     * The entity state is extracted from the event and passed to this routing schema.
     *
     * @return event route for [EntityStateChanged] events.
     */
    public fun eventRoute(): EventRoute<I, EntityStateChanged> {
        return EventRoute { event: EntityStateChanged, context: EventContext ->
            @Suppress("UNCHECKED_CAST") // `EntityStateChanged` passes the `EntityState`s.
            val state = AnyPacker.unpack(event.newState) as EntityState<I>
            invoke(state, context)
        }
    }

    public companion object {

        /**
         * Creates a new `StateUpdateRouting`.
         *
         * The resulting routing schema has the ignoring default route,
         * i.e., if a custom route is not set, the entity state update is ignored.
         *
         * @param I The type of the entity IDs to which the updates are routed.
         * @param idClass The class of identifiers served by this routing.
         * @return new `StateUpdateRouting`.
         */
        @JvmStatic
        public fun <I : Any> newInstance(idClass: Class<I>): StateUpdateRouting<I> =
            StateUpdateRouting(idClass)
    }
}
