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

package io.spine.server.aggregate.given.aggregate;

import com.google.common.collect.ImmutableList;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.aggregate.given.dispatch.AggregateMessageDispatcher;
import io.spine.server.command.Assign;
import io.spine.server.entity.rejection.StandardRejections;
import io.spine.server.event.NoReaction;
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

import java.util.List;

import static io.spine.server.aggregate.given.Given.EventMessage.projectCreated;
import static io.spine.server.aggregate.given.Given.EventMessage.projectStarted;
import static io.spine.server.aggregate.given.Given.EventMessage.taskAdded;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.env;

/**
 * An aggregate class with handlers and appliers.
 */
@SuppressWarnings({"PublicField", "WeakerAccess"}) /* For inspection in tests. */
public class TestAggregate
        extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

    @VisibleForTesting
    public boolean createProjectCommandHandled = false;
    @VisibleForTesting
    public boolean addTaskCommandHandled = false;
    @VisibleForTesting
    public boolean startProjectCommandHandled = false;
    @VisibleForTesting
    public boolean projectCreatedEventApplied = false;
    @VisibleForTesting
    public boolean taskAddedEventApplied = false;
    @VisibleForTesting
    public boolean projectStartedEventApplied = false;
    @VisibleForTesting
    public boolean rejectionHandled = false;
    @VisibleForTesting
    public boolean rejectionWithCmdHandled = false;

    public TestAggregate(ProjectId id) {
        super(id);
    }

    @Assign
    AggProjectCreated handle(AggCreateProject cmd, CommandContext ctx) {
        createProjectCommandHandled = true;
        var event = projectCreated(cmd.getProjectId(), cmd.getName());
        return event;
    }

    @Assign
    AggTaskAdded handle(AggAddTask cmd) {
        addTaskCommandHandled = true;
        var event = taskAdded(cmd.getProjectId());
        return event.toBuilder()
                    .setTask(cmd.getTask())
                    .build();
    }

    @Assign
    List<AggProjectStarted> handle(AggStartProject cmd) {
        startProjectCommandHandled = true;
        var message = projectStarted(cmd.getProjectId());
        return ImmutableList.of(message);
    }

    @Apply
    private void event(AggProjectCreated e) {
        builder().setId(e.getProjectId())
                 .setStatus(Status.CREATED);

        projectCreatedEventApplied = true;
    }

    @Apply
    private void event(AggTaskAdded e) {
        taskAddedEventApplied = true;
        builder().addTask(e.getTask());
    }

    @Apply
    private void event(AggProjectStarted e) {
        builder().setId(e.getProjectId())
                 .setStatus(Status.STARTED);

        projectStartedEventApplied = true;
    }

    @React
    AggProjectArchived on(AggProjectDeleted event) {
        return AggProjectArchived
                .newBuilder()
                .setProjectId(event.getProjectId())
                .buildPartial();
    }

    @Apply
    private void event(AggProjectArchived e) {
        builder().setId(e.getProjectId())
                 .setStatus(Status.CANCELLED);
    }

    @React
    NoReaction on(StandardRejections.CannotModifyDeletedEntity rejection, AggAddTask command) {
        rejectionWithCmdHandled = true;
        return noReaction();
    }

    @React
    NoReaction on(StandardRejections.CannotModifyDeletedEntity rejection) {
        rejectionHandled = true;
        return noReaction();
    }

    @VisibleForTesting
    public void dispatchCommands(Command... commands) {
        for (var cmd : commands) {
            AggregateMessageDispatcher.dispatchCommand(this, env(cmd));
        }
    }
}
