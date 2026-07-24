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

package io.spine.server

import io.spine.server.aggregate.Aggregate
import io.spine.server.aggregate.DefaultAggregateRepository
import io.spine.server.entity.Entity
import io.spine.server.entity.Repository
import io.spine.server.entity.model.EntityClass
import io.spine.server.procman.DefaultProcessManagerRepository
import io.spine.server.procman.ProcessManager
import io.spine.server.projection.DefaultProjectionRepository
import io.spine.server.projection.Projection

/**
 * The marker interface for the repositories that do not require creating a custom subclass
 * of [Repository].
 *
 * If no customization is required, [of] is the easiest way to create
 * an instance of a repository for a given entity type.
 */
public interface DefaultRepository {

    public companion object {

        /**
         * Creates a default repository for the passed entity class.
         *
         * Default repositories are useful when no customization (for example, custom routing)
         * is required for managing entities of the passed class.
         *
         * @param I The type of entity identifiers.
         * @param E The type of the entity.
         * @param cls The class of entities.
         * @return a new repository instance.
         */
        @JvmStatic
        @Suppress(
            "UNCHECKED_CAST", // Casts are ensured by the class assignability checks below.
            "DEPRECATION"     // The deprecated `AggregatePart` API is supported until its removal.
        )
        public fun <I : Any, E : Entity<I, *>> of(cls: Class<E>): Repository<I, E> {
            /*
             * We deliberately "save" on OOP here and detect the class by the chain of `when`
             * branches below (instead of implementing this using the methods in the
             * `EntityClass` hierarchy). This is done to provide more convenient syntax for
             * our framework users.
             */
            val anyClass = cls as Class<Nothing>
            val repository: Repository<*, *> = when {
                io.spine.server.aggregate.AggregatePart::class.java.isAssignableFrom(cls) ->
                    defaultAggregatePartRepository(anyClass)
                Aggregate::class.java.isAssignableFrom(cls) ->
                    DefaultAggregateRepository<Nothing, Nothing, Nothing>(anyClass)
                ProcessManager::class.java.isAssignableFrom(cls) ->
                    DefaultProcessManagerRepository<Nothing, Nothing, Nothing>(anyClass)
                Projection::class.java.isAssignableFrom(cls) ->
                    DefaultProjectionRepository<Nothing, Nothing, Nothing>(anyClass)
                else -> throw IllegalArgumentException(
                    "No default repository implementation available for the class `$cls`."
                )
            }
            return repository as Repository<I, E>
        }
    }
}

// Extracted from `of()` because the deprecated type's fully qualified name,
// together with its type arguments, does not fit on one line at the call site.
@Suppress("DEPRECATION")
private fun defaultAggregatePartRepository(cls: Class<Nothing>): Repository<*, *> =
    io.spine.server.aggregate.DefaultAggregatePartRepository<Nothing, Nothing, Nothing, Nothing>(
        cls
    )

/**
 * Obtains the logging name of a default repository that manages entities described
 * by the given [modelClass].
 */
internal fun defaultRepositoryLogName(modelClass: EntityClass<*>): String =
    "${DefaultRepository::class.java.simpleName}.of(${modelClass.rawClass().simpleName}.class)"
