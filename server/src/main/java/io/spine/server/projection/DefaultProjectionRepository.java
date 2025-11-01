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

package io.spine.server.projection;

import io.spine.annotation.Internal;
import io.spine.base.ProjectionState;
import io.spine.server.DefaultRepository;
import io.spine.server.projection.model.ProjectionClass;

import static io.spine.server.projection.model.ProjectionClass.asProjectionClass;

/**
 * Default implementation of {@code ProjectionRepository}.
 *
 * @param <I>
 *         the type of IDs of projections
 * @param <P>
 *         the type of projections
 * @param <S>
 *         the type of projection state messages
 * @see io.spine.server.DefaultRepository
 */
@Internal
public class DefaultProjectionRepository<I,
                                         P extends Projection<I, S, ?>,
                                         S extends ProjectionState<I>>
        extends ProjectionRepository<I, P, S>
        implements DefaultRepository {

    private final ProjectionClass<P> modelClass;

    /**
     * Creates a new repository managing projections of the passed class.
     */
    public DefaultProjectionRepository(Class<P> cls) {
        super();
        this.modelClass = asProjectionClass(cls);
    }

    @Override
    public ProjectionClass<P> entityModelClass() {
        return modelClass;
    }

    @Override
    public String toString() {
        return logName();
    }
}
