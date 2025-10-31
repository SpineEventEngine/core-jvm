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

package io.spine.server.procman.migration;

import io.spine.annotation.Experimental;
import io.spine.base.ProcessManagerState;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Migration;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.ProcessManagerMigration;
import io.spine.validate.ValidatingBuilder;

/**
 * A migration operation that marks a {@link ProcessManager} as {@link Entity#isDeleted() deleted}.
 *
 * <p>When applied to an entity, it will modify the {@code deleted} flag of a corresponding
 * storage record to be {@code true}.
 *
 * @param <I>
 *         the type of the process manager identifiers
 * @param <P>
 *         the type of the process managers
 * @param <S>
 *         the type of process manager's state
 * @param <B>
 *         the type of the validating builders for the state
 * @see io.spine.server.entity.RecordBasedRepository#applyMigration(Object, Migration)
 */
@Experimental
public final class MarkPmDeleted<I,
                                 P extends ProcessManager<I, S, B>,
                                 S extends ProcessManagerState<I>,
                                 B extends ValidatingBuilder<S>>
        extends ProcessManagerMigration<I, P, S, B> {

    @Override
    public S apply(S s) {
        markDeleted();
        return s;
    }
}
