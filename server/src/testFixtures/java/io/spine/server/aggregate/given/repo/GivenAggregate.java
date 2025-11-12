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

import io.spine.base.CommandMessage;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.entity.given.Given;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.testdata.Sample;

import static io.spine.server.aggregate.given.dispatch.AggregateMessageDispatcher.dispatchCommand;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.requestFactory;
import static io.spine.testdata.Sample.builderForType;

/**
 * Utility factory for test aggregates.
 **/
public class GivenAggregate {

    private final AggregateRepository<ProjectId, ProjectAggregate, AggProject> repository;

    public GivenAggregate(AggregateRepository<ProjectId, ProjectAggregate, AggProject> repository) {
        this.repository = repository;
    }

    public ProjectAggregate withUncommittedEvents() {
        return withUncommittedEvents(Sample.messageOfType(ProjectId.class));
    }

    public ProjectAggregate withUncommittedEvents(ProjectId id) {
        var givenProject =
                Given.aggregateOfClass(ProjectAggregate.class)
                     .withId(id)
                     .build();

        AggCreateProject.Builder createProject = builderForType(AggCreateProject.class);
        createProject.setProjectId(id);
        AggAddTask.Builder addTask = builderForType(AggAddTask.class);
        addTask.setProjectId(id);
        AggStartProject.Builder startProject = builderForType(AggStartProject.class);
        startProject.setProjectId(id);

        new ToProject(givenProject)
                .dispatch(createProject.build())
                .dispatch(addTask.build())
                .dispatch(startProject.build());
        return givenProject;
    }

    /**
     * Method object which simplifies dispatching of commands to {@link ProjectAggregate} instances.
     */
    private final class ToProject {

        private final ProjectAggregate aggregate;

        private ToProject(ProjectAggregate aggregate) {
            this.aggregate = aggregate;
        }

        private ToProject dispatch(CommandMessage commandMessage) {
            dispatchCommand(aggregate, repository, env(commandMessage));
            return this;
        }

        /** Generates a command for the passed message and wraps it into the envelope. */
        private CommandEnvelope env(CommandMessage commandMessage) {
            return CommandEnvelope.of(requestFactory().command()
                                                      .create(commandMessage));
        }
    }
}
