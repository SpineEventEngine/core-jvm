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
import io.spine.base.EventMessage
import io.spine.base.Routable
import io.spine.core.CommandContext
import io.spine.core.EventContext
import io.spine.core.SignalContext
import java.lang.reflect.Method

/**
 * Abstract base for signal routing methods scanned by a [RoutingMap].
 *
 * @param I The type of the entity identifiers.
 * @param M The type of the signals routed by the method.
 * @param C The type of the signal context routed by the method.
 *
 * @property rawMethod The routing method declared by an entity class.
 *
 * @see Route
 * @see RoutingMap
 */
internal sealed class RoutingMethod<I : Any, M : Routable, C : SignalContext, R : Any>(
    protected val rawMethod: Method
) {
    /**
     * Tells if the [rawMethod] accepts a parameter for a signal context.
     */
    private val acceptsContext: Boolean = rawMethod.parameterTypes.size == 2

    /**
     * Invokes the [rawMethod] with the given parameters.
     */
    protected open fun doInvoke(message: M, context: C): Any? {
        rawMethod.setAccessible(true)
        val result = if (acceptsContext) {
            rawMethod.invoke(null, message, context)
        } else {
            rawMethod.invoke(null, message)
        }
        return result
    }
}

/**
 * The routing method for command messages.
 */
internal class CommandRoutingMethod<I : Any>(
    rawMethod: Method
) : RoutingMethod<I, CommandMessage, CommandContext, I>(rawMethod),
    CommandRoute<I, CommandMessage> {

    override fun invoke(message: CommandMessage, context: CommandContext): I {
        val result = doInvoke(message, context)
        @Suppress("UNCHECKED_CAST")
        return result as I
    }
}

/**
 * The routing method for event messages.
 */
internal class EventRoutingMethod<I: Any>(
    rawMethod: Method
) : RoutingMethod<I, EventMessage, EventContext, Set<I>>(rawMethod),
    EventRoute<I, EventMessage> {

    /**
     * Is `true` if the method returns `Set<I>`. Otherwise, the method returns `I`.
     */
    private val returnsSet: Boolean = rawMethod.returnType == Set::class.java

    @Suppress("UNCHECKED_CAST") /* The casts are protected by:
        1) the return type check when the entity class methods are analyzed.
        2) the `returnsSet` property.
    */
    override fun invoke(message: EventMessage, context: EventContext): Set<I> {
        val result = doInvoke(message, context)
        return if (returnsSet) {
            result as Set<I>
        } else {
            setOf(result as I)
        }
    }
}
