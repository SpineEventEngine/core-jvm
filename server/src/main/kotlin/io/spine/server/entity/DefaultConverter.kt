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

import com.google.protobuf.FieldMask
import io.spine.base.EntityState
import io.spine.type.TypeUrl

/**
 * Default implementation of [StorageConverter] for [AbstractEntity].
 *
 * @param I The type of entity IDs.
 * @param E The type of entities.
 * @param S The type of entity states.
 */
internal class DefaultConverter<I : Any, E : AbstractEntity<I, S>, S : EntityState<I>>
private constructor(
    stateType: TypeUrl,
    factory: EntityFactory<E>,
    fieldMask: FieldMask
) : StorageConverter<I, E, S>(stateType, factory, fieldMask) {

    override fun withFieldMask(fieldMask: FieldMask): StorageConverter<I, E, S> =
        DefaultConverter(entityStateType(), entityFactory(), fieldMask)

    override fun updateBuilder(builder: EntityRecord.Builder, entity: E) {
        // Do nothing here.
    }

    /**
     * Injects the state into an entity.
     *
     * @param entity The entity to inject the state into.
     * @param state The state message to inject.
     * @param entityRecord The [EntityRecord] which contains additional attributes
     *   that may be injected.
     */
    override fun injectState(entity: E, state: S, entityRecord: EntityRecord) {
        entity.updateState(state, entityRecord.version)
        entity.setLifecycleFlags(entityRecord.lifecycleFlags())
    }

    companion object {

        /**
         * Creates a converter which copies all the fields of the entity state.
         */
        fun <I : Any, E : AbstractEntity<I, S>, S : EntityState<I>> forAllFields(
            stateType: TypeUrl,
            factory: EntityFactory<E>
        ): StorageConverter<I, E, S> =
            DefaultConverter(stateType, factory, FieldMask.getDefaultInstance())
    }
}
