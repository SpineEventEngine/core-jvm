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
import io.spine.base.Routable
import io.spine.core.SignalContext

/**
 * An abstract base for routing schemas that route a message to potentially more than one entity.
 *
 * Some kinds of messages such as [EventMessage][io.spine.base.EventMessage]s or
 * [EntityState][io.spine.base.EntityState] messages can be dispatched to several entities.
 * This kind of routing is called [multicast][Multicast], and routing functions of such
 * schemas return a set of identifiers of the type [I].
 *
 * ## Unicast routing
 *
 * In some cases, a routing function returns only one identifier.
 * This class provides convenience methods called [unicast] that allow to adapt
 * function returning a single identifier to the general contract of the schema.
 *
 * ```kotlin
 * routing.route<MyMessage> { message, context -> ... }
 *        .unicast<AnotherMessage> { message, context -> ... }
 *        .unicast<YetAnother> { message -> ... }
 * ```
 * The class provides overloads of the [unicast] functions that accept
 * a message and its context, or only a message.
 */
public abstract class MulticastRouting<
        I : Any,
        M : Routable,
        C : SignalContext,
        R : Set<I>,
        S : MulticastRouting<I, M, C, R, S>>(
    defaultRoute: RouteFn<M, C, R>
) : MessageRouting<I, M, C, R, S>(defaultRoute) {

    /**
     * Adds a route for the messages with the given type [N].
     *
     * @param N The type of the message which descends from
     *   the super-interface [M] served by this routing schema.
     * @param via The route function to be used for this type of messages.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if the route for this message class is already set either
     *   directly or via a super-interface.
     */
    @CanIgnoreReturnValue
    public inline fun <reified N : M> unicast(
        noinline via: (N, C) -> I
    ): S = unicast(N::class.java, via)

    /**
     * Adds a route for the messages with the given type [N].
     *
     * @param N The type of the message which descends from
     *   the super-interface [M] served by this routing schema.
     * @param via The route function to be used for this type of messages.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if the route for this message class is already set either
     *   directly or via a super-interface.
     */
    @CanIgnoreReturnValue
    public inline fun <reified N : M> unicast(
        noinline via: (N) -> I
    ): S = unicast(N::class.java, via)

    /**
     * Adds a route for the messages with the given type [N].
     *
     * This is the Java version of `public inline fun` [unicast].
     *
     * @param N The type of the message which descends from
     *   the super-interface [M] served by this routing schema.
     * @param via The route function to be used for this type of messages.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if the route for this message class is already set either
     *   directly or via a super-interface.
     */
    @CanIgnoreReturnValue
    public fun <N : M> unicast(
        msgType: Class<N>,
        via: (N, C) -> I
    ): S = route(msgType, createUnicastRoute(via))

    /**
     * Adds a route for the messages with the given type [N].
     *
     * This is the Java version of `public inline fun` [unicast].
     *
     * @param N The type of the message which descends from
     *   the super-interface [M] served by this routing schema.
     * @param via The route function to be used for this type of messages.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if the route for this message class is already set either
     *   directly or via a super-interface.
     */
    @CanIgnoreReturnValue
    public fun <N : M> unicast(
        msgType: Class<N>,
        via: (N) -> I
    ): S = route(msgType, createUnicastRoute(via))

    /**
     * Adapt the single parameter function to [RouteFn] instance of the type
     * used by the descendant of this class.
     */
    protected abstract fun <N : M> createUnicastRoute(via: (N) -> I): RouteFn<N, C, R>

    /**
     * Adapts the function with two parameters to [RouteFn] instance of
     * the type used by the descendant of this class.
     */
    protected abstract fun <N : M> createUnicastRoute(via: (N, C) -> I): RouteFn<N, C, R>
}
