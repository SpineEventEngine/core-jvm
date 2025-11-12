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

@file:JvmName("Contexts")

package io.spine.server

import io.spine.base.EntityState
import io.spine.server.entity.Entity
import kotlin.reflect.KClass

/**
 * Adds the specified entity class to this `BoundedContextBuilder`.
 *
 * A default repository instance will be created for this class.
 * This instance will be added to the repository registration list for
 * the bounded context being built.
 *
 * @param I The type of entity identifiers.
 * @param E The type of entities.
 */
public inline fun <reified I : Any, reified S : EntityState<I>, reified E : Entity<I, out S>>
        BoundedContextBuilder.add(entity: KClass<out E>) {
    add(entity.java)
}

/**
 * Tells if the bounded context has entities of the given type.
 */
public inline fun <reified E: Entity<*, *>> BoundedContext.hasEntitiesOfType(): Boolean =
    hasEntitiesOfType(E::class.java)

/**
 * Tells if the bounded context has entities with the given state type.
 */
public inline fun <reified S: EntityState<*>> BoundedContext.hasEntitiesWithState(): Boolean =
    hasEntitiesWithState(S::class.java)
