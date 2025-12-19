/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.system.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.base.CommandMessage;
import io.spine.base.Error;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.core.ActorContext;
import io.spine.core.BoundedContextName;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandId;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContext;
import io.spine.server.DefaultRepository;
import io.spine.system.server.event.CommandAcknowledged;
import io.spine.system.server.event.CommandDispatched;
import io.spine.system.server.event.CommandErrored;
import io.spine.system.server.event.CommandHandled;
import io.spine.system.server.event.CommandReceived;
import io.spine.system.server.event.CommandRejected;
import io.spine.system.server.event.TargetAssignedToCommand;
import io.spine.system.server.given.command.CommandLifecycleWatcher;
import io.spine.system.server.given.command.CompanyAggregate;
import io.spine.system.server.given.command.CompanyNameProcman;
import io.spine.system.server.given.command.CompanyNameProcmanRepo;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenUserId;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.type.TypeUrl;
import io.spine.validation.ValidationError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.protobuf.Messages.isNotDefault;
import static io.spine.system.server.SystemBoundedContexts.systemOf;
import static io.spine.system.server.given.command.CompanyNameProcman.FAULTY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`CommandLog` should")
class CommandLogTest {

    private static final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(EntityEventsTest.class);

    private BoundedContext context;
    private BoundedContext system;

    @BeforeEach
    void setUp() {
        var contextName = BoundedContextName.newBuilder()
                .setValue(EntityEventsTest.class.getSimpleName())
                .build();
        context = BoundedContext.singleTenant(contextName.getValue()).build();
        system = systemOf(context);

        var ctx = context.internalAccess();
        ctx.register(DefaultRepository.of(CompanyAggregate.class));
        ctx.register(new CompanyNameProcmanRepo());
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @DisplayName("produce system events when command")
    @Nested
    class ProduceEvents {

        private CommandLifecycleWatcher eventAccumulator;
        private CompanyId id;

        @BeforeEach
        void setUp() {
            this.eventAccumulator = new CommandLifecycleWatcher();
            system.eventBus().register(eventAccumulator);
            id = CompanyId.generate();
        }

        @Test
        @DisplayName("is processed successfully")
        void successful() {
            var successfulCommand = EstablishCompany.newBuilder()
                    .setId(id)
                    .setFinalName("Good company name")
                    .build();
            var commandId = postCommand(successfulCommand);
            checkReceived(successfulCommand);
            checkAcknowledged(commandId);
            checkDispatched(commandId);
            checkTargetAssigned(commandId, CompanyAggregate.TYPE);
            checkHandled(commandId);
        }

        @Test
        @DisplayName("is filtered out")
        void invalid() {
            var invalidCommand = buildInvalidCommand();
            var actualId = postBuiltCommand(invalidCommand);

            checkReceived(unpack(invalidCommand.getMessage()));
            var expectedId = invalidCommand.getId();
            assertEquals(expectedId, actualId);

            var error = checkErrored(actualId);
            assertTrue(error.hasDetails());
            var details = AnyPacker.unpack(error.getDetails());
            assertThat(details).isInstanceOf(ValidationError.class);
            assertTrue(isNotDefault(details));
        }

        @Test
        @DisplayName("is rejected by handler")
        void rejected() {
            var rejectedCommand = EstablishCompany.newBuilder()
                    .setId(id)
                    .setFinalName(CompanyAggregate.TAKEN_NAME)
                    .build();
            var commandId = postCommand(rejectedCommand);
            checkReceived(rejectedCommand);

            eventAccumulator.assertReceivedEvent(CommandAcknowledged.class);
            eventAccumulator.assertReceivedEvent(CommandDispatched.class);
            eventAccumulator.assertReceivedEvent(TargetAssignedToCommand.class);

            checkRejected(commandId, Rejections.CompanyNameAlreadyTaken.class);
        }

        @Test
        @DisplayName("causes a runtime exception")
        @MuteLogging
        void errored() {
            var start = StartCompanyEstablishing.newBuilder()
                    .setId(id)
                    .build();
            var startId = postCommand(start);

            eventAccumulator.assertEventCount(5);

            checkReceived(start);
            checkAcknowledged(startId);
            checkDispatched(startId);
            checkTargetAssigned(startId, CompanyNameProcman.TYPE);
            checkHandled(startId);

            eventAccumulator.forgetEvents();

            var propose = ProposeCompanyName.newBuilder()
                    .setId(id)
                    .setName(FAULTY_NAME)
                    .build();
            var proposeId = postCommand(propose);

            eventAccumulator.assertEventCount(5);

            checkReceived(propose);
            checkAcknowledged(proposeId);
            checkDispatched(proposeId);
            checkTargetAssigned(proposeId, CompanyNameProcman.TYPE);
            var error = checkHandlerFailed(proposeId);
            assertThat(error.getType())
                    .isEqualTo(IllegalArgumentException.class.getCanonicalName());
        }

        private Command buildInvalidCommand() {
            var invalidCommand = EstablishCompany.getDefaultInstance();
            var actor = GivenUserId.newUuid();
            var now = Time.currentTime();
            var actorContext = ActorContext.newBuilder()
                    .setTimestamp(now)
                    .setActor(actor)
                    .build();
            var context = CommandContext.newBuilder()
                    .setActorContext(actorContext)
                    .build();
            var command = Command.newBuilder()
                    .setId(CommandId.generate())
                    .setMessage(AnyPacker.pack(invalidCommand))
                    .setContext(context)
                    .build();
            return command;
        }

        private void checkReceived(Message expectedCommand) {
            var received = eventAccumulator.assertReceivedEvent(CommandReceived.class);
            var actualCommand = unpack(received.getPayload().getMessage());
            assertEquals(expectedCommand, actualCommand);
        }

        private void checkAcknowledged(CommandId commandId) {
            var acknowledged = eventAccumulator.assertReceivedEvent(CommandAcknowledged.class);
            assertEquals(commandId, acknowledged.getId());
        }

        private void checkDispatched(CommandId commandId) {
            var dispatched = eventAccumulator.assertReceivedEvent(CommandDispatched.class);
            assertEquals(commandId, dispatched.getId());
        }

        private void checkTargetAssigned(CommandId commandId, TypeUrl entityType) {
            var assigned =
                    eventAccumulator.assertReceivedEvent(TargetAssignedToCommand.class);
            var target = assigned.getTarget();
            var actualId = target.getEntityId().getId();
            assertEquals(commandId, assigned.getId());
            assertEquals(id, Identifier.unpack(actualId));
            assertEquals(entityType.value(), target.getTypeUrl());
        }

        private void checkHandled(CommandId commandId) {
            var handled = eventAccumulator.assertReceivedEvent(CommandHandled.class);
            assertEquals(commandId, handled.getId());
        }

        @CanIgnoreReturnValue
        private Error checkErrored(CommandId commandId) {
            var errored = eventAccumulator.assertReceivedEvent(CommandErrored.class);
            assertEquals(commandId, errored.getId());
            return errored.getError();
        }

        @CanIgnoreReturnValue
        private Error checkHandlerFailed(CommandId commandId) {
            var errored =
                    eventAccumulator.assertReceivedEvent(HandlerFailedUnexpectedly.class);
            var signalId = errored.getHandledSignal();
            assertTrue(signalId.isCommand());
            assertEquals(commandId, signalId.asCommandId());
            return errored.getError();
        }

        private void checkRejected(CommandId commandId,
                                   Class<? extends Message> expectedRejectionClass) {
            var rejected = eventAccumulator.assertReceivedEvent(CommandRejected.class);
            assertEquals(commandId, rejected.getId());
            var rejectionEvent = rejected.getRejectionEvent();
            var rejectionType = rejectionEvent.enclosedTypeUrl();
            var expectedType = TypeUrl.of(expectedRejectionClass);
            assertEquals(expectedType, rejectionType);
        }

        private CommandId postCommand(CommandMessage commandMessage) {
            var command = requestFactory.createCommand(commandMessage);
            return postBuiltCommand(command);
        }

        private CommandId postBuiltCommand(Command command) {
            var commandBus = context.commandBus();
            commandBus.post(command, noOpObserver());
            return command.getId();
        }
    }
}
