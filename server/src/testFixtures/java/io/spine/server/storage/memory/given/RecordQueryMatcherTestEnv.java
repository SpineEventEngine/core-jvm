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

package io.spine.server.storage.memory.given;

import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;
import io.spine.query.Subject;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;

/**
 * The test environment for {@link io.spine.server.storage.memory.RecordQueryMatcher} tests.
 */
@SuppressWarnings("BadImport")       // `create` looks fine in this context.
public final class RecordQueryMatcherTestEnv {

    /** Prevents instantiation of this test env class. */
    private RecordQueryMatcherTestEnv() {
    }

    /**
     * Creates an empty {@code Subject} for the {@code StgProject}.
     */
    public static Subject<StgProjectId, StgProject> recordSubject() {
        return RecordQuery.newBuilder(StgProjectId.class, StgProject.class)
                .build()
                .subject();
    }

    /**
     * Creates a {@code Subject} for the {@code EntityRecord} with the given ID.
     */
    public static Subject<StgProjectId, StgProject> recordSubject(StgProjectId id) {
        return RecordQuery.newBuilder(parameterizedClsOf(id), StgProject.class)
                .id().is(id)
                .build()
                .subject();
    }

    @SuppressWarnings("unchecked" /* As per the declaration. */)
    private static <I> Class<I> parameterizedClsOf(I id) {
        return (Class<I>) id.getClass();
    }

    /**
     * Creates a new query builder targeting stored {@code StgProject} instances.
     */
    public static RecordQueryBuilder<StgProjectId, StgProject> newQueryBuilder() {
        return RecordQuery.newBuilder(StgProjectId.class, StgProject.class);
    }
}
