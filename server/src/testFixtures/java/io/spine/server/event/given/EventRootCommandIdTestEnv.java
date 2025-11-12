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

package io.spine.server.event.given;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.net.EmailAddress;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.event.EventStreamQuery;
import io.spine.server.event.React;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.EventRouting;
import io.spine.test.event.EvInvitationAccepted;
import io.spine.test.event.EvMember;
import io.spine.test.event.EvMemberInvitation;
import io.spine.test.event.EvTeam;
import io.spine.test.event.EvTeamCreation;
import io.spine.test.event.EvTeamId;
import io.spine.test.event.EvTeamMemberAdded;
import io.spine.test.event.EvTeamMemberInvited;
import io.spine.test.event.EvTeamProjectAdded;
import io.spine.test.event.EvUserSignUp;
import io.spine.test.event.Project;
import io.spine.test.event.ProjectCreated;
import io.spine.test.event.ProjectId;
import io.spine.test.event.Task;
import io.spine.test.event.TaskAdded;
import io.spine.test.event.command.CreateProject;
import io.spine.test.event.command.EvAcceptInvitation;
import io.spine.test.event.command.EvAddTasks;
import io.spine.test.event.command.EvAddTeamMember;
import io.spine.test.event.command.EvInviteTeamMembers;
import io.spine.testing.client.TestActorRequestFactory;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Identifier.newUuid;
import static io.spine.testdata.Sample.builderForType;
import static java.util.Collections.singleton;

public class EventRootCommandIdTestEnv {

    public static final TenantId TENANT_ID = tenantId();

    private static final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(EventRootCommandIdTestEnv.class, TENANT_ID);

    /** Prevents instantiation of this utility class. */
    private EventRootCommandIdTestEnv() {
    }

    public static ProjectId projectId() {
        return ProjectId
                .newBuilder()
                .setId(newUuid())
                .build();
    }

    public static EvTeamId teamId() {
        return EvTeamId
                .newBuilder()
                .setId(newUuid())
                .build();
    }

    private static TenantId tenantId() {
        var value = EventRootCommandIdTestEnv.class.getName();
        var id = TenantId.newBuilder()
                .setValue(value)
                .build();
        return id;
    }

    public static Command command(CommandMessage message) {
        return requestFactory.createCommand(message);
    }

    public static CreateProject createProject(ProjectId projectId, EvTeamId teamId) {
        checkNotNull(projectId);
        checkNotNull(teamId);

        return CreateProject.newBuilder()
                            .setProjectId(projectId)
                            .setTeamId(teamId)
                            .build();
    }

    public static EvAddTasks addTasks(ProjectId id, int count) {
        checkNotNull(id);

        var builder = EvAddTasks.newBuilder();
        for (var i = 0; i < count; i++) {
            var task = (Task) builderForType(Task.class).build();
            builder.addTask(task);
        }

        return builder.setProjectId(id)
                      .build();
    }

    public static EvAddTeamMember addTeamMember(EvTeamId teamId) {
        checkNotNull(teamId);

        return ((EvAddTeamMember.Builder) builderForType(EvAddTeamMember.class))
                .setTeamId(teamId)
                .build();
    }

    public static EvAcceptInvitation acceptInvitation(EvTeamId teamId) {
        checkNotNull(teamId);

        var invitation = memberInvitation(teamId);
        return ((EvAcceptInvitation.Builder) builderForType(EvAcceptInvitation.class))
                .setInvitation(invitation)
                .build();
    }

    public static EvInviteTeamMembers inviteTeamMembers(EvTeamId teamId, int count) {
        checkNotNull(teamId);

        var builder = EvInviteTeamMembers.newBuilder();
        for (var i = 0; i < count; i++) {
            var task = (EmailAddress) builderForType(EmailAddress.class).build();
            builder.addEmail(task);
        }

        return builder.setTeamId(teamId)
                      .build();
    }

    private static EvMemberInvitation memberInvitation(EvTeamId teamId) {
        return ((EvMemberInvitation.Builder) builderForType(EvMemberInvitation.class))
                .setTeamId(teamId)
                .build();
    }

    /**
     * Creates a new {@link EventStreamQuery} without any filters.
     */
    public static EventStreamQuery allEventsQuery() {
        return EventStreamQuery.newBuilder()
                               .build();
    }

    public static class ProjectAggregateRepository
            extends AggregateRepository<ProjectId, ProjectAggregate, Project> {
    }

    /**
     * Routes the {@link ProjectCreated} event to the {@link TeamAggregate} the project belongs to.
     * This is done for the purposes of the
     * {@linkplain EventRootMessageIdTest.MatchExternalEventHandledBy#aggregate()} test.
     */
    public static class TeamAggregateRepository
            extends AggregateRepository<EvTeamId, TeamAggregate, EvTeam> {

        @Override
        protected void setupEventRouting(EventRouting<EvTeamId> routing) {
            super.setupEventRouting(routing);
            routing.route(ProjectCreated.class, (msg, ctx) -> singleton(msg.getTeamId()));
        }
    }

    /**
     * Routes the {@link EvInvitationAccepted} event to the {@link TeamCreationProcessManager} which
     * created the invitation. This is done for the purposes of the
     * {@linkplain EventRootMessageIdTest.MatchExternalEventHandledBy#processManager()} test.
     */
    public static final class TeamCreationRepository
            extends ProcessManagerRepository<EvTeamId, TeamCreationProcessManager, EvTeamCreation> {

        @Override
        @OverridingMethodsMustInvokeSuper
        protected void setupEventRouting(EventRouting<EvTeamId> routing) {
            super.setupEventRouting(routing);
            routing.route(EvInvitationAccepted.class,
                          (msg, ctx) -> singleton(msg.getInvitation().getTeamId()));
        }
    }

    public static class UserSignUpRepository
            extends ProcessManagerRepository<UserId, UserSignUpProcessManager, EvUserSignUp> {
    }

    static class ProjectAggregate extends Aggregate<ProjectId, Project, Project.Builder> {

        private ProjectAggregate(ProjectId id) {
            super(id);
        }

        @Assign
        ProjectCreated on(CreateProject command, CommandContext ctx) {
            var event = ProjectCreated
                    .newBuilder()
                    .setProjectId(command.getProjectId())
                    .setTeamId(command.getTeamId())
                    .build();
            return event;
        }

        @Assign
        List<TaskAdded> on(EvAddTasks command, CommandContext ctx) {
            ImmutableList.Builder<TaskAdded> events = ImmutableList.builder();

            for (var task : command.getTaskList()) {
                var event = TaskAdded
                        .newBuilder()
                        .setProjectId(command.getProjectId())
                        .setTask(task)
                        .build();
                events.add(event);
            }

            return events.build();
        }

        @Apply
        private void event(ProjectCreated event) {
            builder()
                    .setId(event.getProjectId())
                    .setStatus(Project.Status.CREATED);
        }

        @Apply
        private void event(TaskAdded event) {
            builder()
                    .setId(event.getProjectId())
                    .addTask(event.getTask());
        }
    }

    static class TeamAggregate extends Aggregate<EvTeamId, EvTeam, EvTeam.Builder> {

        private TeamAggregate(EvTeamId id) {
            super(id);
        }

        @React
        EvTeamProjectAdded on(ProjectCreated command) {
            var event = projectAdded(id(), command);
            return event;
        }

        @Apply
        private void event(EvTeamProjectAdded event) {
            builder()
                    .setId(event.getTeamId())
                    .addProjectId(event.getProjectId());
        }

        private static EvTeamProjectAdded projectAdded(EvTeamId id, ProjectCreated command) {
            return EvTeamProjectAdded
                    .newBuilder()
                    .setTeamId(id)
                    .setProjectId(command.getProjectId())
                    .build();
        }
    }

    static class TeamCreationProcessManager
            extends ProcessManager<EvTeamId, EvTeamCreation, EvTeamCreation.Builder> {

        private TeamCreationProcessManager(EvTeamId id) {
            super(id);
        }

        @Assign
        EvTeamMemberAdded on(EvAddTeamMember command, CommandContext ctx) {
            builder().setId(id())
                     .addMember(command.getMember());
            var event = memberAdded(command.getMember());
            return event;
        }

        @Assign
        List<EvTeamMemberInvited> on(EvInviteTeamMembers command, CommandContext ctx) {
            builder().setId(id());
            ImmutableList.Builder<EvTeamMemberInvited> events = ImmutableList.builder();

            for (var email : command.getEmailList()) {

                var invitation = memberInvitation(email);
                builder().addInvitation(invitation);

                var event = teamMemberInvited(email);
                events.add(event);
            }

            return events.build();
        }

        @React
        EvTeamMemberAdded on(EvInvitationAccepted event) {
            builder().setId(id());
            var member = member(event.getUserId());
            var newEvent = memberAdded(member);
            return newEvent;
        }

        private EvTeamMemberAdded memberAdded(EvMember member) {
            return EvTeamMemberAdded.newBuilder()
                    .setTeamId(id())
                    .setMember(member)
                    .build();
        }

        private EvTeamMemberInvited teamMemberInvited(EmailAddress email) {
            return EvTeamMemberInvited.newBuilder()
                    .setTeamId(id())
                    .setEmail(email)
                    .build();
        }

        private static EvMemberInvitation memberInvitation(EmailAddress email) {
            return EvMemberInvitation.newBuilder()
                    .setEmail(email)
                    .build();
        }

        private static EvMember member(UserId userId) {
            return EvMember.newBuilder()
                    .setUserId(userId)
                    .build();
        }
    }

    static class UserSignUpProcessManager
            extends ProcessManager<UserId, EvUserSignUp, EvUserSignUp.Builder> {

        private UserSignUpProcessManager(UserId id) {
            super(id);
        }

        @Assign
        EvInvitationAccepted on(EvAcceptInvitation command, CommandContext ctx) {
            builder()
                    .setUserId(id())
                    .setInvitation(command.getInvitation());
            var event = invitationAccepted(command.getInvitation());
            return event;
        }

        private EvInvitationAccepted invitationAccepted(EvMemberInvitation invitation) {
            return EvInvitationAccepted.newBuilder()
                    .setInvitation(invitation)
                    .setUserId(id())
                    .build();
        }
    }
}
