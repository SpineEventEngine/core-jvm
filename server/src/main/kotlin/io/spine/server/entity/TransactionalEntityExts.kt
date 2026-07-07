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

@file:JvmName("TransactionalEntityExtensions")

package io.spine.server.entity

import io.spine.base.EntityState
import io.spine.core.Version
import io.spine.validation.ValidatingBuilder

/**
 * Obtains the entity identifier.
 *
 * This is a shortcut for `id()`.
 *
 * @param I The type of the entity identifiers.
 * @param E The type of the transactional entity.
 * @param S The type of the entity state.
 * @param B The type of the entity state builder.
 */
public val <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.id: I
    get() = id()

/**
 * Obtains the entity version.
 *
 * This is a shortcut for `version()`.
 *
 * @param I The type of the entity identifiers.
 * @param E The type of the transactional entity.
 * @param S The type of the entity state.
 * @param B The type of the entity state builder.
 */
public val <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.version: Version
    get() = version()

/**
 * Obtains the entity state.
 *
 * This is a shortcut for `state()`.
 *
 * @param I The type of the entity identifiers.
 * @param E The type of the transactional entity.
 * @param S The type of the entity state.
 * @param B The type of the entity state builder.
 */
public val <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.state: S
    get() = state()
