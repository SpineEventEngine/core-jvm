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

package io.spine.server.procman;

import io.spine.annotation.Internal;
import io.spine.base.ProcessManagerState;
import io.spine.server.DefaultRepository;
import io.spine.server.procman.model.ProcessManagerClass;

import static io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass;

/**
 * Default implementation of {@code ProcessManagerRepository}.
 *
 * @param <I>
 *         the type of IDs of process managers
 * @param <P>
 *         the type of process managers
 * @param <S>
 *         the type of process manager state messages
 * @see io.spine.server.DefaultRepository
 */
@Internal
public final class DefaultProcessManagerRepository<I,
                                                   P extends ProcessManager<I, S, ?>,
                                                   S extends ProcessManagerState<I>>
        extends ProcessManagerRepository<I, P, S>
        implements DefaultRepository {

    private final ProcessManagerClass<P> modelClass;

    /**
     * Creates a new repository managing process managers of the passed class.
     */
    public DefaultProcessManagerRepository(Class<P> cls) {
        super();
        this.modelClass = asProcessManagerClass(cls);
    }

    @Override
    public ProcessManagerClass<P> entityModelClass() {
        return modelClass;
    }

    @Override
    public String toString() {
        return logName();
    }
}
