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
import io.spine.server.command.Assign;
import io.spine.server.event.React;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.Status;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.event.AggProjectArchived;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggProjectDeleted;
import io.spine.test.aggregate.event.AggProjectStarted;
import io.spine.test.aggregate.event.AggTaskAdded;

import java.util.Optional;

/**
 * The main aggregate class for positive scenarios tests.
 */
public class ProjectAggregate
        extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

    private ProjectAggregate(ProjectId id) {
        super(id);
    }

    @Assign
    AggProjectCreated handle(AggCreateProject msg) {
        return AggProjectCreated.newBuilder()
                .setProjectId(msg.getProjectId())
                .setName(msg.getName())
                .build();
    }

    @Apply
    private void apply(AggProjectCreated event) {
        builder().setId(event.getProjectId())
                 .setName(event.getName());
    }

    @Assign
    AggTaskAdded handle(AggAddTask msg) {
        return AggTaskAdded.newBuilder()
                .setProjectId(msg.getProjectId())
                .setTask(msg.getTask())
                .build();
    }

    @Apply
    private void apply(AggTaskAdded event) {
        builder().setId(event.getProjectId())
                 .addTask(event.getTask());
    }

    @Assign
    AggProjectStarted handle(AggStartProject msg) {
        return AggProjectStarted.newBuilder()
                .setProjectId(msg.getProjectId())
                .build();
    }

    @Apply
    private void apply(AggProjectStarted event) {
        builder().setStatus(Status.STARTED);
    }

    /**
     * Emits {@link io.spine.test.aggregate.event.AggProjectArchived} if the event is from
     * the parent project.
     *
     * <p>Otherwise returns an empty iterable.
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

    @Apply
    private void apply(AggProjectArchived event) {
        archive();
    }

    /**
     * Emits {@link io.spine.test.aggregate.event.AggProjectDeleted} if the event is from
     * the parent project.
     *
     * <p>Otherwise returns an empty iterable.
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

    public final void archive() {
        setArchived(true);
    }
}
