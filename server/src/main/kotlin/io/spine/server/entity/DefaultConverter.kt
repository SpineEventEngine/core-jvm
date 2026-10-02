/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.entity

import io.spine.base.EntityState
import io.spine.type.TypeUrl

/**
 * Default implementation of [StorageConverter] for [AbstractEntity].
 *
 * @param I The type of entity IDs.
 * @param E The type of entities.
 * @param S The type of entity states.
 * @param stateType The type URL of the state of entities that this converter builds.
 * @param factory The factory that creates the entities.
 */
internal class DefaultConverter<I : Any, E : AbstractEntity<I, S>, S : EntityState<I>>(
    stateType: TypeUrl,
    factory: EntityFactory<E>
) : StorageConverter<I, E, S>(stateType, factory) {

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
}
