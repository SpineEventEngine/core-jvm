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

package io.spine.server.entity;

import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.core.Version;
import io.spine.reflect.GenericTypeIndex;
import io.spine.server.entity.model.EntityClass;
import io.spine.string.Stringifiers;

/**
 * A server-side object with an {@link Identifier#checkSupported(Class) identity}.
 *
 * <p>A state of an entity is defined as a Protobuf message.
 *
 * <p>Lifecycle flags determine if an entity is active.
 * An entity is considered to be active if the lifecycle flags are not set.
 * If an entity is {@linkplain #isArchived() archived} or {@linkplain #isDeleted() deleted},
 * then it’s regarded as inactive.
 *
 * @param <I>
 *         the type of the entity identifier
 * @param <S>
 *         the type of the entity state
 */
public interface Entity<I, S extends EntityState<I>> extends WithLifecycle {

    /**
     * Obtains the identifier of the entity.
     */
    I id();

    /**
     * Obtains string representation of the entity identifier.
     *
     * @apiNote The primary purpose of this method is to display the identifier in
     *         human-readable form in debug and error messages.
     */
    default String idAsString() {
        return Stringifiers.toString(id());
    }

    /**
     * Obtains the state of the entity.
     */
    S state();

    /**
     * Tells whether lifecycle flags of the entity have been changed since its initialization.
     */
    boolean lifecycleFlagsChanged();

    /**
     * Obtains the version of the entity.
     */
    Version version();

    /**
     * Obtains the model class for this entity.
     */
    EntityClass<?> modelClass();

    /**
     * Enumeration of generic type parameters of this interface.
     */
    enum GenericParameter implements GenericTypeIndex<Entity<?, ?>> {

        /**
         * The index of the declaration of the generic parameter type {@code <I>} in
         * the {@link Entity} interface.
         */
        ID(0),

        /**
         * The index of the declaration of the generic parameter type {@code <S>}
         * in the {@link Entity} interface.
         */
        STATE(1);

        private final int index;

        GenericParameter(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }
}
