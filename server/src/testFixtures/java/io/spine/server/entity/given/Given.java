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

package io.spine.server.entity.given;

import io.spine.base.AggregateState;
import io.spine.base.ProcessManagerState;
import io.spine.base.ProjectionState;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.given.dispatch.AggregateBuilder;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.given.dispatch.ProcessManagerBuilder;
import io.spine.server.projection.Projection;
import io.spine.server.projection.given.dispatch.ProjectionBuilder;
import io.spine.validate.ValidatingBuilder;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility class for building test objects.
 */
public class Given {

    /** Prevents instantiation of this utility class. */
    private Given() {
    }

    /**
     * Creates a dynamic builder for building an {@code Aggregate}.
     */
    public static <A extends Aggregate<I, S, ?>, I, S extends AggregateState<I>>
    AggregateBuilder<A, I, S> aggregateOfClass(Class<A> aggregateClass) {
        checkNotNull(aggregateClass);
        var result = new AggregateBuilder<A, I, S>();
        result.setResultClass(aggregateClass);
        return result;
    }

    /**
     * Creates a builder for a {@code Projection}.
     */
    public static <P extends Projection<I, S, B>,
                   I,
                   S extends ProjectionState<I>,
                   B extends ValidatingBuilder<S>>
    ProjectionBuilder<P, I, S, B> projectionOfClass(Class<P> projectionClass) {
        checkNotNull(projectionClass);
        var result = new ProjectionBuilder<P, I, S, B>();
        result.setResultClass(projectionClass);
        return result;
    }

    /**
     * Creates a builder for a {@code ProcessManager}.
     */
    public static <P extends ProcessManager<I, S, B>,
                   I,
                   S extends ProcessManagerState<I>,
                   B extends ValidatingBuilder<S>>
    ProcessManagerBuilder<P, I, S, B> processManagerOfClass(Class<P> pmClass) {
        checkNotNull(pmClass);
        ProcessManagerBuilder<P, I, S, B> result = ProcessManagerBuilder.newInstance();
        result.setResultClass(pmClass);
        return result;
    }
}
