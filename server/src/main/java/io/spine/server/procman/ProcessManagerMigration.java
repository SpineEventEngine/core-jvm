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

import io.spine.annotation.Experimental;
import io.spine.base.ProcessManagerState;
import io.spine.server.entity.Migration;
import io.spine.server.entity.Transaction;
import io.spine.validate.ValidatingBuilder;

/**
 * A {@link Migration} applied to a {@link ProcessManager} instance.
 *
 * @param <I>
 *         the type of process manager identifiers
 * @param <P>
 *         the type of process managers
 * @param <S>
 *         the type of the state of the migrated process managers
 * @param <B>
 *         the type of validation builders for the process manager's state
 */
@Experimental
public abstract class ProcessManagerMigration<I,
                                              P extends ProcessManager<I, S, B>,
                                              S extends ProcessManagerState<I>,
                                              B extends ValidatingBuilder<S>>
        extends Migration<I, P, S, B> {

    @SuppressWarnings("unchecked") // Logically correct.
    @Override
    protected Transaction<I, P, S, B> startTransaction(P entity) {
        var tx = (Transaction<I, P, S, B>) new PmTransaction<>(entity);
        return tx;
    }
}
