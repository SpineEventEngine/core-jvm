/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity

import io.spine.base.EntityState
import io.spine.core.Version
import io.spine.reflect.GenericTypeIndex
import io.spine.string.Stringifiers

/**
 * A server-side object with an [identity][io.spine.base.Identifier].
 *
 * A state of an entity is defined as a Protobuf message.
 *
 * Lifecycle flags determine if an entity is active. An entity is considered to be active if
 * the lifecycle flags are not set. If an entity is [archived][isArchived] or
 * [deleted][isDeleted], then it is regarded as inactive.
 *
 * @param I The type of the entity identifier.
 * @param S The type of the entity state.
 */
public interface Entity<I : Any, S : EntityState<I>> : WithLifecycle {

    /**
     * Obtains the identifier of the entity.
     */
    public fun id(): I

    /**
     * Obtains string representation of the entity identifier.
     *
     * The primary purpose of this method is to display the identifier in
     * human-readable form in debug and error messages.
     */
    public fun idAsString(): String = Stringifiers.toString(id())

    /**
     * Obtains the state of the entity.
     */
    public fun state(): S

    /**
     * Tells whether lifecycle flags of the entity have been changed since its initialization.
     */
    public fun lifecycleFlagsChanged(): Boolean

    /**
     * Obtains the version of the entity.
     */
    public fun version(): Version

    /**
     * Enumeration of generic type parameters of this interface.
     */
    public enum class GenericParameter(private val indexValue: Int) :
        GenericTypeIndex<Entity<*, *>> {

        /**
         * The index of the declaration of the generic parameter type `I` in
         * the [Entity] interface.
         */
        ID(0),

        /**
         * The index of the declaration of the generic parameter type `S`
         * in the [Entity] interface.
         */
        STATE(1);

        override fun index(): Int = indexValue
    }
}
