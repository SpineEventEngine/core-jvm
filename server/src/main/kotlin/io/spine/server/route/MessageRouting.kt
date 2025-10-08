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
import io.spine.base.Routable
import io.spine.core.SignalContext
import java.util.Collections.synchronizedMap

/**
 * An abstract base for classes implementing routing schemas for [routable][Routable] messages
 * such as commands, events, rejections, or entity states.
 *
 * Routing schemas are used by [repositories][io.spine.server.entity.Repository] for delivering
 * messages to entities. The identifiers of the target entities are calculated by these schemas.
 *
 * A routing schema consists of a [default route][defaultRoute] and custom routes per a message
 * class or an interface. The default route is used when none of the routing functions
 * added to the schema for a class or an interface matches the incoming message.
 *
 * ## Composing a routing schema
 * A routing schema is composed using the [route] functions:
 *
 * ```kotlin
 * routing.route<MyClass1> { message, context -> ... }
 *        .route<MyClass2> { message ->  ... }
 *        .route<MyInterface> { message, context ->  ... }
 *        .route<MoreAbstractInterface> { message ->  ... }
 * ```
 * Entries for classes must come before those for interfaces.
 * Entries for more specific interfaces must be added before entries for super-interfaces of
 * the already added interface entries. There rules ensure correct routing of messages.
 * If these rules are not followed, `IllegalStateException` will be thrown when calling
 * a [route] function which attempts to add a violating entry.
 *
 * ## Optional `context` parameter
 *
 * A routing function may accept two parameters `message` and `context`, or only
 * single `message` parameter, if the message context does not participate in the routing.
 *
 * ## Replacing the default route
 *
 * Repositories create [MessageRouting] instances for their needs providing default routes
 * that match the nature of the dispatched messages. An alternative default route can be set
 * using the [replaceDefault] function.
 *
 * @param I The type of identifiers used in the routing.
 * @param M The common supertype of messages to route.
 * @param C The type of message context.
 * @param R The type returned by a [routing function][RouteFn.invoke].
 *   For unicast routing it would be the same as [I], and `Set<I>` for multicast routing.
 * @param S The type of message routing for covariance in return types.
 * @property defaultRoute The route to be used if there is no matching entry set in [routes].
 */
@Suppress("TooManyFunctions")
public sealed class MessageRouting<
        I : Any,
        M : Routable,
        C : SignalContext,
        R : Any,
        S : MessageRouting<I, M, C, R, S>
        >(
    private var defaultRoute: RouteFn<M, C, R>
) : RouteFn<M, C, R> {

    /**
     * Provides the type reference to `this` for covariance of the type returned
     * by [route] functions.
     */
    protected abstract fun self(): S

    /**
     * Maps a message class to the function for calculating identifier(s) of
     * entities to deliver a message of this class.
     *
     * ### Implementation note
     *
     * This collection is made `synchronized`, since in some cases it is
     * being simultaneously read and modified in different threads.
     * In particular, the modification at run-time is performed when storing
     * the routes discovered on a per-interface basis.
     * Therefore, if this collection is not `synchronized`,
     * a `ConcurrentModificationException` is sometimes thrown.
     */
    private val routes: MutableMap<Class<M>, RouteFn<M, C, R>> =
        synchronizedMap(LinkedHashMap())

    /**
     * Obtains the default route to be used when none of the added entries match
     * the argument passed to the [invoke] function.
     */
    @VisibleForTesting
    public fun defaultRoute(): RouteFn<M, C, R> = defaultRoute

    /**
     * Sets a new default route in the schema.
     *
     * @param newDefault The new route to be used as default.
     */
    @CanIgnoreReturnValue
    public fun replaceDefault(newDefault: RouteFn<M, C, out R>): S {
        @Suppress("UNCHECKED_CAST")
        defaultRoute = newDefault as RouteFn<M, C, R>
        return self()
    }

    /**
     * Adds a route for the given message type [N].
     *
     * @param N The type of the message which descends from the type
     *   [M] served by this routing schema.
     * @param via The route to be used for this type of messages.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if the route for this message class is already set either
     *   directly or via a super-interface.
     */
    @CanIgnoreReturnValue
    public inline fun <reified N: M> route(via: RouteFn<N, C, R>): S =
        route(N::class.java, via)

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
    public inline fun <reified N: M> route(noinline via: (N) -> R): S =
        route<N> { n, _ -> via(n) }

    /**
     * Adds a route for the given message type [N].
     *
     * This is the Java version of `public inline fun` [route].
     *
     * @param N The type of the message which descends from
     *   the super-interface [M] served by this routing schema.
     * @param via The route function to be used for this type of messages.
     * @return `this` to allow chained calls when configuring the routing.
     * @throws IllegalStateException if the route for this message class is already set either
     *   directly or via a super-interface.
     */
    @CanIgnoreReturnValue
    public fun <N : M> route(msgClass: Class<N>, via: RouteFn<N, C, R>): S {
        addRoute(msgClass, via)
        return self()
    }

    /**
     * Checks if the passed message type is supported by this routing schema.
     */
    public open fun supports(messageType: Class<out M>): Boolean {
        val match = routeFor(messageType)
        val result = match.found
        return result
    }

    /**
     * Adds a custom route for the given message type.
     *
     * @param N The type derived from common type M served by this routing.
     *   Could be a sub-interface or a class.
     * @param messageType The type of messages to route.
     * @param via The route function to be used for this type.
     * @throws IllegalStateException If the route for this message class is already set either
     *   directly or via a super-interface.
     */
    private fun <N : M> addRoute(messageType: Class<N>, via: RouteFn<N, C, R>) {
        val match = routeFor(messageType)
        if (match.found) {
            val requestedClass = messageType.name
            val entryClass: String = match.entryClass!!.name
            if (match.isDirect) {
                error(
                    "The route for the message class $requestedClass` already set." +
                            " Please remove the route (`$entryClass`) before setting a new one."
                )
            } else {
                error(
                    "The route for the message class `$requestedClass` already defined via" +
                            " the interface `$entryClass`. If you want to have specific" +
                            " routing for the class `$requestedClass`," +
                            " please put it before the routing for the super-interface.",
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        routes[messageType as Class<M>] = via as RouteFn<M, C, R>
    }

    /**
     * Obtains a routing function for the given class of messages, or `null`
     * if the schema does not serve the class via non-default routes.
     */
    protected open fun <N : M> find(cls: Class<N>): RouteFn<N, C, R>? {
        val match = routeFor(cls)
        return if (match.found) {
            @Suppress("UNCHECKED_CAST") // protected by generics when adding entries.
            match.route as RouteFn<N, C, R>
        } else {
            null
        }
    }

    /**
     * Obtains a route for the passed message class.
     *
     * @param msgCls the class of the messages.
     * @return optionally available route.
     */
    internal fun routeFor(msgCls: Class<out M>): Match {
        val direct = findDirect(msgCls)
        if (direct.found) {
            return direct
        }
        val viaInterface = findViaInterface(msgCls)
        return if (viaInterface.found) {
            // Store the found route for later direct use.
            @Suppress("UNCHECKED_CAST")
            routes[msgCls as Class<M>] = viaInterface.route!!
            viaInterface
        } else {
            notFound(msgCls)
        }
    }

    private fun findDirect(msgCls: Class<out M>): Match {
        val route = routes[msgCls]
        if (route != null) {
            return Match(msgCls, msgCls, route)
        }
        return notFound(msgCls)
    }

    private fun findViaInterface(msgCls: Class<out M>): Match {
        val result = routes.keys
            .filter { c -> c.isInterface }
            .find { iface -> iface.isAssignableFrom(msgCls) }
            ?.let { iface ->
                Match(msgCls, iface, routes[iface])
            }
            ?: (notFound(msgCls))
        return result
    }

    /**
     * Removes a route for the passed message class.
     *
     * @throws IllegalStateException if a custom route for this class was not previously set.
     */
    public fun remove(messageClass: Class<out M>) {
        if (!routes.containsKey(messageClass)) {
            error(
                "Cannot remove the route for the message class `${messageClass.name}`:" +
                        " a custom route was not previously set.",
            )
        }
        routes.remove(messageClass)
    }

    /**
     * Removes a route for the given message class.
     *
     * @param N The message type of the routing function to remove.
     * @throws IllegalStateException if a custom route for this message class was not
     *   previously set or already removed.
     */
    public inline fun <reified N : M> remove(): Unit =
        remove(N::class.java)

    /**
     * Obtains IDs of entities to which the passed message should be delivered.
     *
     * If there is no function for the passed message, applies the default function.
     *
     * @param message The message to route.
     * @param context The context of the message.
     * @return the set of entity IDs to which the message should be delivered.
     */
    override fun invoke(message: M, context: C): R {
        val cls = message.javaClass
        val match = routeFor(cls)
        if (match.found) {
            val func = match.route!!
            val result = func.invoke(message, context)
            return result
        }
        val result = defaultRoute().invoke(message, context)
        return result
    }

    /**
     * Provides the result of finding a route in the routing schema.
     *
     * @param requestedClass The class of the message which needs to be routed.
     * @param entryClass The type through which the route is found.
     *   Can be a class (for the [isDirect] match) or a super-interface
     *   of the requested class.
     *   Is `null` if there is no routing found for the [requestedClass].
     * @param route The routing function or `null` if there is no route defined neither
     *   for the class nor a super-interface of the class.
     */
    internal inner class Match(
        private val requestedClass: Class<out M>,
        val entryClass: Class<out M>?,
        val route: RouteFn<M, C, R>?
    ) {
        val found: Boolean = route != null

        /**
         * Returns `true` if the routing was defined directly for the requested class,
         * otherwise `false`.
         */
        val isDirect: Boolean = requestedClass == entryClass
    }

    private fun notFound(requestedClass: Class<out M>): Match = Match(requestedClass, null, null)
}
