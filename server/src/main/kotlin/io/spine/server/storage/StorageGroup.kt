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

package io.spine.server.storage

import io.spine.base.EntityState
import io.spine.server.entity.Entity
import io.spine.server.entity.model.EntityClass
import io.spine.type.TypeName

/**
 * A named group differentiating the record storages that hold records of
 * the same type.
 *
 * Several storages of a Bounded Context may store records of one type. For
 * example, the latest state of an entity and the history of its past states
 * are both stored as `EntityRecord`s. Without a further distinction, a storage
 * vendor mapping equal record specifications to one physical storage would
 * conflate them. A `StorageGroup` provides that distinction, so each group is
 * allocated its own physical storage — a table, a kind, and the like.
 *
 * The [name] is assigned by the repository creating the storage — typically
 * after the entity state, via [of]. Choosing the value is the repository's
 * decision; this type only carries it.
 *
 * @property name The name of the storage group.
 */
public data class StorageGroup(public val name: String) {

    public companion object {

        /**
         * Creates a group for the entities of the given class, named after
         * the qualified Protobuf name of their state.
         *
         * @param entityClass The class of the entities served by the storage.
         */
        @JvmStatic
        public fun of(entityClass: Class<out Entity<*, *>>): StorageGroup {
            val stateClass = EntityClass.stateClassOf<EntityState<*>>(entityClass)
            val name = TypeName.of(stateClass).value()
            return StorageGroup(name)
        }
    }
}
