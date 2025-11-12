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

package io.spine.server;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.client.ActorRequestFactory;
import io.spine.client.Query;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.people.PersonName;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.entity.EntityRecord;
import io.spine.server.event.AbstractEventReactor;
import io.spine.server.event.React;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.Status;
import io.spine.test.aggregate.Task;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.event.AggOwnerNotified;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggProjectStarted;
import io.spine.test.aggregate.event.AggTaskAdded;
import io.spine.test.bc.event.BcProjectCreated;
import io.spine.test.commandservice.customer.Customer;
import io.spine.test.commandservice.customer.CustomerId;
import io.spine.test.commandservice.customer.command.CreateCustomer;
import io.spine.test.commandservice.customer.event.CustomerCreated;
import io.spine.test.subscriptionservice.ReportId;
import io.spine.test.subscriptionservice.command.SendReport;
import io.spine.test.subscriptionservice.event.ReportSent;
import io.spine.test.projection.BankAccount;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenUserId;
import io.spine.time.Now;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;

public class Given {

    private Given() {
    }

    static class EventMessage {

        private EventMessage() {
        }

        static AggTaskAdded taskAdded(ProjectId id) {
            return AggTaskAdded.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        static AggProjectCreated projectCreated(ProjectId id) {
            return AggProjectCreated.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        static AggProjectStarted projectStarted(ProjectId id) {
            return AggProjectStarted.newBuilder()
                    .setProjectId(id)
                    .build();
        }
    }

    static class CommandMessage {

        private CommandMessage() {
        }

        public static AggCreateProject createProject(ProjectId id) {
            return AggCreateProject.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        public static SendReport sendReport() {
            return SendReport.newBuilder()
                    .setId(ReportId.generate())
                    .build();
        }
    }

    static class ACommand {

        private static final UserId USER_ID = GivenUserId.newUuid();
        private static final ProjectId PROJECT_ID = newProjectId();

        /* This hack is just for the testing purposes.
        The production code should use more sane approach to generating the IDs. */
        private static final AtomicInteger customerNumber = new AtomicInteger(1);

        private ACommand() {
        }

        /**
         * Creates a new {@code ACommand} with the given command message, userId and
         * timestamp using default {@code ACommand} instance.
         */
        private static Command create(io.spine.base.CommandMessage command,
                                      UserId userId,
                                      Timestamp when) {
            var generatedTenantId = TenantId.newBuilder()
                    .setValue(newUuid())
                    .build();
            var factory = new TestActorRequestFactory(userId, generatedTenantId);
            var result = factory.createCommand(command, when);
            return result;
        }

        static Command createProject() {
            return createProject(currentTime());
        }

        private static Command createProject(Timestamp when) {
            return createProject(USER_ID, PROJECT_ID, when);
        }

        private static Command createProject(UserId userId, ProjectId projectId, Timestamp when) {
            var command = CommandMessage.createProject(projectId);
            return create(command, userId, when);
        }

        static Command createCustomer() {
            var localDate = Now.get().asLocalDate();
            var customerId = CustomerId.newBuilder()
                    .setRegistrationDate(localDate)
                    .setNumber(customerNumber.get())
                    .build();
            customerNumber.incrementAndGet();
            var personName = PersonName.newBuilder()
                    .setGivenName("Kreat")
                    .setFamilyName("C'Ustomer")
                    .setHonorificSuffix("Cmd")
                    .build();
            var customer = Customer.newBuilder()
                    .setId(customerId)
                    .setName(personName)
                    .build();
            io.spine.base.CommandMessage msg = CreateCustomer.newBuilder()
                    .setCustomerId(customerId)
                    .setCustomer(customer)
                    .build();
            var userId = GivenUserId.of(Identifier.newUuid());
            var result = create(msg, userId, currentTime());
            return result;
        }

        private static ProjectId newProjectId() {
            return ProjectId.generate();
        }
    }

    static class AQuery {

        private static final ActorRequestFactory requestFactory =
                new TestActorRequestFactory(AQuery.class);

        private AQuery() {
        }

        static Query readAllProjects() {
            // DO NOT replace the type name with another Project class.
            var result = requestFactory.query()
                                       .all(io.spine.test.projection.Project.class);
            return result;
        }

        static Query readUnknownType() {
            var result = requestFactory.query()
                                       .all(Task.class);
            return result;
        }

        static Query readInternalType() {
            var result = requestFactory.query()
                    .all(BankAccount.class);
            return result;
        }
    }

    static class ProjectAggregateRepository
            extends AggregateRepository<ProjectId, ProjectAggregate, AggProject> {

        ProjectAggregateRepository() {
            super();
        }
    }

    private static class ProjectAggregate
            extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

        // an aggregate constructor must be public because it is used via reflection
        @SuppressWarnings("PublicConstructorInNonPublicClass")
        public ProjectAggregate(ProjectId id) {
            super(id);
        }

        @Assign
        AggProjectCreated handle(AggCreateProject cmd, CommandContext ctx) {
            return EventMessage.projectCreated(cmd.getProjectId());
        }

        @Assign
        AggTaskAdded handle(AggAddTask cmd, CommandContext ctx) {
            return EventMessage.taskAdded(cmd.getProjectId());
        }

        @Assign
        List<AggProjectStarted> handle(AggStartProject cmd, CommandContext ctx) {
            var message = EventMessage.projectStarted(cmd.getProjectId());
            return ImmutableList.of(message);
        }

        @Apply
        private void event(AggProjectCreated event) {
            builder().setId(event.getProjectId())
                     .setStatus(Status.CREATED);
        }

        @Apply
        private void event(AggTaskAdded event) {
        }

        @Apply
        private void event(AggProjectStarted event) {
            builder().setId(event.getProjectId())
                     .setStatus(Status.STARTED);
        }
    }

    static class AggProjectCreatedReactor extends AbstractEventReactor {

        @React
        AggOwnerNotified on(AggProjectCreated event, EventContext context) {
            return AggOwnerNotified
                    .newBuilder()
                    .setOwner(context.actor())
                    .build();
        }
    }

    static class ReportSender extends AbstractAssignee {

        @Assign
        ReportSent on(SendReport command) {
            return ReportSent
                    .newBuilder()
                    .setId(command.getId())
                    .build();
        }
    }

    public static class CustomerAggregateRepository
            extends AggregateRepository<CustomerId, CustomerAggregate, Customer> {

        private @Nullable TargetFilters memoizedFilters;
        private @Nullable ResponseFormat memoizedFormat;

        public CustomerAggregateRepository() {
            super();
        }

        @Override
        public Iterator<EntityRecord> findRecords(TargetFilters filters, ResponseFormat format) {
            this.memoizedFilters = filters;
            this.memoizedFormat = format;
            return super.findRecords(filters, format);
        }

        @Internal
        @Override
        public Iterator<EntityRecord> findRecords(ResponseFormat format) {
            this.memoizedFormat = format;
            return super.findRecords(format);
        }

        public Optional<TargetFilters> memoizedFilters() {
            return Optional.ofNullable(memoizedFilters);
        }

        public Optional<ResponseFormat> memoizedFormat() {
            return Optional.ofNullable(memoizedFormat);
        }
    }

    public static class CustomerAggregate
            extends Aggregate<CustomerId, Customer, Customer.Builder> {

        @Assign
        CustomerCreated handle(CreateCustomer cmd, CommandContext ctx) {
            var event = CustomerCreated.newBuilder()
                    .setCustomerId(cmd.getCustomerId())
                    .setCustomer(cmd.getCustomer())
                    .build();
            return event;
        }

        @Apply
        private void event(CustomerCreated event) {
            builder().mergeFrom(event.getCustomer());
        }
    }

    /*
     * `QueryServiceTest` environment.
     ***************************************************/

    static final String PROJECTS_CONTEXT_NAME = "Projects";

    static final String CUSTOMERS_CONTEXT_NAME = "Customers";

    static class ProjectDetailsRepository
            extends ProjectionRepository<io.spine.test.projection.ProjectId,
                                         ProjectDetails,
                                         io.spine.test.projection.Project> {
    }

    /**
     * A {@link ProjectDetailsRepository} which throws on attempt to
     * {@link #findRecords(ResponseFormat) load all records}.
     */
    static final class ThrowingProjectDetailsRepository
            extends ProjectDetailsRepository {

        @Internal
        @Override
        public Iterator<EntityRecord> findRecords(ResponseFormat format) {
            throw new IllegalStateException("Ignore this error.");
        }
    }

    static class ProjectDetails
            extends Projection<io.spine.test.projection.ProjectId,
                               io.spine.test.projection.Project,
                               io.spine.test.projection.Project.Builder> {

        private ProjectDetails(io.spine.test.projection.ProjectId id) {
            super(id);
        }

        @SuppressWarnings("UnusedParameters") // OK for test method.
        @Subscribe
        void on(BcProjectCreated event, EventContext context) {
            // Do nothing.
        }
    }
}
