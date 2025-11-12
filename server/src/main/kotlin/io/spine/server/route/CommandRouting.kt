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

import io.spine.base.CommandMessage
import io.spine.base.RejectionThrowable
import io.spine.core.CommandContext

/**
 * A [routing schema][MessageRouting] for commands.
 *
 * Commands are always delivered to a single entity, following a routing
 * strategy known as [unicast][Unicast].
 * The `CommandRouting` schema contains entries that implement the [CommandRoute] interface.
 * This interface is a functional type that returns an entity identifier of type [I].
 *
 * ## Default command route
 *
 * By default, `CommandRouting` uses the first field declared in a command message
 * to determine the route. This behavior is defined by the [DefaultCommandRoute],
 * which is passed to the constructor of `CommandRouting` when the [newInstance] factory
 * function is called.
 *
 * To customize the default routing for your repository, call [replaceDefault] in
 * the `setupCommandRouting` method of the corresponding repository class.
 *
 * ## Adding custom routes
 *
 * You may need to define a custom route if the first field of a command message
 * does not match the type of the entity ID that handles the command, as required
 * by the [DefaultCommandRoute].
 * Alternatively, a custom route may be necessary if the entity ID must be derived from
 * both the command message and its context.
 *
 * ## Routing commands with a common interface
 *
 * Routing entries can be defined for either specific command classes or interfaces.
 * If you need to define routes for both command classes and an interface that these
 * classes implement, interface routes should be declared *after* the entries for specific classes:
 *
 * ```kotlin
 * routing.route<MyCommandClass> { command, context -> ... }
 *        .route<AnotherCommand> { command ->  ... }
 *        .route<MyInterface> { command, context ->  ... }
 *        .route<MySuperInterface> { command ->  ... }
 * ```
 *
 * Additionally, define entries for interfaces in order of specificity, starting with
 * more specific interfaces before their super-interfaces. Failure to follow these rules
 * will result in an `IllegalStateException` when calling [route].
 *
 * @param I The type of entity IDs used by this routing schema.
 * @param defaultRoute The fallback route to use when no custom route is [set][route].
 *
 * @see io.spine.server.procman.ProcessManagerRepository.setupCommandRouting
 * @see io.spine.server.aggregate.AggregateRepository.setupCommandRouting
 */
public class CommandRouting<I : Any> private constructor(
    defaultRoute: CommandRoute<I, CommandMessage>
) : MessageRouting<
        I,
        CommandMessage,
        CommandContext,
        I,
        CommandRouting<I>
        >(defaultRoute), CommandRoute<I, CommandMessage> {

    override fun self(): CommandRouting<I> = this

    /**
     * Obtains a route for the passed command class.
     *
     * @param C The type of the command message.
     * @return optionally available route for [C].
     */
    public inline fun <reified C : CommandMessage> find(): CommandRoute<I, C>? =
        find(C::class.java)

    /**
     * Obtains a route for the passed command class.
     *
     * @param cls The class of the command messages.
     * @param C The type of the command message.
     * @return optionally available route for [C].
     */
    public override fun <C : CommandMessage> find(cls: Class<C>): CommandRoute<I, C>? =
        super.find(cls) as CommandRoute<I, C>?

    public companion object {

        /**
         * Creates a new command routing.
         *
         * @param I The type of entity identifiers returned by a new routing.
         * @param idClass The class of target entity identifiers.
         * @return new routing instance.
         */
        @JvmStatic
        public fun <I: Any> newInstance(idClass: Class<I>): CommandRouting<I> {
            val defaultRoute = DefaultCommandRoute.newInstance(idClass)
            return CommandRouting(defaultRoute)
        }

        /**
         * Tells that the [command] cannot be routed by throwing [IllegalStateException].
         *
         * The function is expected to be called by command routing functions
         * when they cannot route a command.
         *
         * @param cause The cause of not being able to route a command.
         *
         * @throws IllegalStateException always.
         */
        @JvmStatic
        public fun unableToRoute(command: CommandMessage, cause: RejectionThrowable) {
            throw IllegalStateException("Unable to route the command `$command`.", cause)
        }
    }
}
