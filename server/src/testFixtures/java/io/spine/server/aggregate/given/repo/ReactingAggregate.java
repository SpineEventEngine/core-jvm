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

package io.spine.server.aggregate.given.repo;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.event.React;
import io.spine.test.aggregate.ParentState;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.event.AggProjectArchived;
import io.spine.test.aggregate.event.AggProjectDeleted;

import java.util.Optional;

/**
 * An aggregate class that reacts only on events and does not handle commands.
 */
public class ReactingAggregate
        extends Aggregate<ProjectId, ParentState, ParentState.Builder> {

    public static final String PROJECT_ARCHIVED = "PROJECT_ARCHIVED";
    public static final String PROJECT_DELETED = "PROJECT_DELETED";

    private ReactingAggregate(ProjectId id) {
        super(id);
    }

    /**
     * Emits {@link io.spine.test.aggregate.event.AggProjectArchived} if the event is from
     * the parent project.
     *
     * <p>Otherwise returns empty iterable.
     */
    @React
    Optional<AggProjectArchived> on(AggProjectArchived event) {
        if (event.getChildProjectIdList()
                 .contains(id())) {
            var reaction = AggProjectArchived.newBuilder()
                    .setProjectId(id())
                    .build();
            return Optional.of(reaction);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Emits {@link io.spine.test.aggregate.event.AggProjectDeleted} if the event is from
     * the parent project.
     *
     * <p>Otherwise returns empty iterable.
     */
    @React
    Optional<AggProjectDeleted> on(AggProjectDeleted event) {
        if (event.getChildProjectIdList()
                 .contains(id())) {
            var reaction = AggProjectDeleted.newBuilder()
                    .setProjectId(id())
                    .build();
            return Optional.of(reaction);
        } else {
            return Optional.empty();
        }
    }

    @Apply
    private void apply(AggProjectArchived event) {
        builder().setValue(PROJECT_ARCHIVED);
    }

    @Apply
    private void apply(AggProjectDeleted event) {
        builder().setValue(PROJECT_DELETED);
    }
}
