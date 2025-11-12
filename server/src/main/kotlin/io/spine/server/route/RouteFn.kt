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

import io.spine.base.Routable
import io.spine.core.SignalContext

/**
 * A common interface for routing functions for calculating identifiers of entities to which
 * a message should be delivered taking the message and its context.
 *
 * Routing functions serve as entries in a [MessageRouting] used by
 * a [repository][io.spine.server.entity.Repository] for delivering the message to
 * entities with the identifier(s) returned by the function.
 *
 * @param M The type of the routed messages.
 * @param C The type of message context.
 * @param R The type of the route function result. Could be one ID type or a set of IDs.
 *
 * @see Unicast
 * @see Multicast
 */
public fun interface RouteFn<M : Routable, C : SignalContext, R : Any> {

    /**
     * Obtains entity ID(s) from the passed message and its context.
     *
     * @param message The message to route.
     * @param context The context of the message.
     * @return identifier(s) of the target entities.
     */
    public operator fun invoke(message: M, context: C): R
}
