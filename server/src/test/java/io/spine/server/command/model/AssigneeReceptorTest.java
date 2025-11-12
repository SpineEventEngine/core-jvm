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

package io.spine.server.command.model;

import com.google.common.testing.NullPointerTester;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.base.CommandMessage;
import io.spine.base.Error;
import io.spine.base.Identifier;
import io.spine.base.RejectionThrowable;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandId;
import io.spine.server.aggregate.given.dispatch.AggregateMessageDispatcher;
import io.spine.server.command.model.given.handler.AssigneeReturnsEmptyList;
import io.spine.server.command.model.given.handler.AssigneeReturnsNothing;
import io.spine.server.command.model.given.handler.InvalidAssigneeNoAnnotation;
import io.spine.server.command.model.given.handler.ProcessManagerDoingNothing;
import io.spine.server.command.model.given.handler.RejectingAggregate;
import io.spine.server.command.model.given.handler.RejectingAssignee;
import io.spine.server.command.model.given.handler.ValidAssigneeOneParam;
import io.spine.server.command.model.given.handler.ValidAssigneeOneParamReturnsList;
import io.spine.server.command.model.given.handler.ValidAssigneeTwoParams;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.model.IllegalOutcomeException;
import io.spine.server.model.ModelError;
import io.spine.server.procman.given.dispatch.PmDispatcher;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.reflect.event.RefProjectCreated;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenCommandContext;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.model.given.Given.CommandMessage.createProject;
import static io.spine.server.model.given.Given.CommandMessage.startProject;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`AssigneeReceptor` should")
@SuppressWarnings("OptionalGetWithoutIsPresent")
class AssigneeReceptorTest {

    private static final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(AssigneeReceptorTest.class);

    private static final CommandContext emptyContext = CommandContext.getDefaultInstance();

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .setDefault(CommandEnvelope.class, generate())
                .setDefault(CommandContext.class, emptyContext)
                .setDefault(Any.class, Any.getDefaultInstance())
                .testAllPublicStaticMethods(AssigneeReceptor.class);
    }

    private static CommandEnvelope generate() {
        return CommandEnvelope.of(requestFactory.generateCommand());
    }

    private static CommandEnvelope newCommand(CommandMessage msg) {
        return CommandEnvelope.of(requestFactory.createCommand(msg));
    }

    @Nested
    @MuteLogging /* Signature mismatch warnings are expected. */
    @DisplayName("invoke assignee method which returns")
    class InvokeAssigneeMethod {

        @Test
        @DisplayName("one `Message`")
        void returningMessage() {
            var assignee = new ValidAssigneeTwoParams();
            var method = new AssigneeSignature().classify(assignee.method());
            assertThat(method).isPresent();

            var handler = method.get();
            var cmd = createProject();
            var envelope = envelope(cmd);
            var outcome = handler.invoke(assignee, envelope);
            var events = outcome.getSuccess().getProducedEvents().getEventList();
            assertThat(assignee.handledCommands()).containsExactly(cmd);
            assertThat(events.size()).isEqualTo(1);

            var event = (RefProjectCreated) events.get(0).enclosedMessage();
            assertThat(event.getProjectId()).isEqualTo(cmd.getProjectId());
        }

        @Test
        @DisplayName("`Message` list")
        void returningMessageList() {
            var assignee = new ValidAssigneeOneParamReturnsList();
            var method = new AssigneeSignature().classify(assignee.method());
            assertThat(method).isPresent();

            var handler = method.get();
            var cmd = createProject();
            var envelope = envelope(cmd);
            var outcome = handler.invoke(assignee, envelope);
            var events = outcome.getSuccess().getProducedEvents().getEventList();
            assertThat(assignee.handledCommands()).containsExactly(cmd);
            assertThat(events.size()).isEqualTo(1);

            var event = (RefProjectCreated) events.get(0).enclosedMessage();
            assertThat(event.getProjectId()).isEqualTo(cmd.getProjectId());
        }
    }

    @Nested
    @DisplayName("throw `IllegalOutcomeException` when invoked method produces")
    class ThrowWhenProduces {

        @Test
        @DisplayName("no events")
        void noEvents() {
            var assignee = new AssigneeReturnsEmptyList();
            var method = new AssigneeSignature().classify(assignee.method());
            assertThat(method).isPresent();

            var handler = method.get();
            var cmd = createProject();
            var envelope = envelope(cmd);
            var outcome = handler.invoke(assignee, envelope);
            assertThat(outcome.hasError()).isTrue();
            assertThat(outcome.getError().getType())
                    .isEqualTo(IllegalOutcomeException.class.getCanonicalName());
        }

        @Test
        @DisplayName("`Nothing` event")
        void nothingEvent() {
            var handlerObject = new AssigneeReturnsNothing();
            var method = new AssigneeSignature().classify(handlerObject.method());
            assertThat(method).isPresent();

            var handler = method.get();
            var cmd = createProject();
            var envelope = envelope(cmd);
            var outcome = handler.invoke(handlerObject, envelope);
            checkIllegalOutcome(outcome, envelope.command());
        }

        @Test
        @DisplayName("`Nothing` event from PM")
        void nothingEventInPm() {
            var commandMessage = createProject();
            var entity = new ProcessManagerDoingNothing(commandMessage.getProjectId().getId());
            var cmd = newCommand(commandMessage);
            var outcome = PmDispatcher.dispatch(entity, cmd);
            checkIllegalOutcome(outcome, cmd.command());
        }

        private void checkIllegalOutcome(DispatchOutcome outcome, Command command) {
            var error = Error.newBuilder()
                    .setType(IllegalOutcomeException.class.getCanonicalName());
            var expected = DispatchOutcome.newBuilder()
                    .setPropagatedSignal(command.messageId())
                    .setError(error)
                    .buildPartial();
            ProtoTruth.assertThat(outcome)
                      .comparingExpectedFieldsOnly()
                      .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("consider assignee method invalid with")
    class ConsiderAssigneeInvalidWith {

        @Test
        @DisplayName("no annotation")
        void noAnnotation() {
            var handler = new InvalidAssigneeNoAnnotation().method();
            assertThat(new AssigneeSignature().matches(handler)).isFalse();
        }
    }

    @Nested
    @DisplayName("set producer ID when dispatching to")
    class SetProducerId {

        @SuppressWarnings("CheckReturnValue") // no need as the call to dispatch() throws
        @Test
        @DisplayName("command assignee method")
        void onDispatchToAssignee() {
            var assignee = new RejectingAssignee();
            var envelope = newCommand(createProject());
            try {
                assignee.dispatch(envelope);
            } catch (IllegalStateException e) {
                assertCauseAndId(e, assignee.id());
            }
        }

        @SuppressWarnings("CheckReturnValue") // no need as the call to dispatchCommand() throws
        @Test
        @DisplayName("entity")
        void onDispatchToEntity() {
            var commandMessage = createProject();
            var entity = new RejectingAggregate(commandMessage.getProjectId());
            var cmd = newCommand(commandMessage);
            try {
                AggregateMessageDispatcher.dispatchCommand(entity, cmd);
            } catch (IllegalStateException e) {
                assertCauseAndId(e, entity.id());
            }
        }

        private void assertCauseAndId(Throwable e, Object handlerId) {
            var cause = getRootCause(e);
            assertThat(cause instanceof RejectionThrowable).isTrue();

            var thrown = (RejectionThrowable) cause;
            var optionalProducerId = thrown.producerId();
            assertThat(optionalProducerId).isPresent();

            var producerId = Identifier.unpack(optionalProducerId.get());
            assertThat(producerId).isEqualTo(handlerId);
        }
    }

    @Test
    @MuteLogging
    @DisplayName("throw `ModelError` when dispatching command without an assignee")
    void notDispatchNonHandledCmd() {
        var assignee = new ValidAssigneeOneParam();
        var cmd = newCommand(startProject());
        assertThrows(ModelError.class, () -> assignee.dispatch(cmd));
    }

    private static CommandEnvelope envelope(Message commandMessage) {
        var cmd = pack(commandMessage);
        var command = Command.newBuilder()
                .setId(CommandId.generate())
                .setMessage(cmd)
                .setContext(GivenCommandContext.withRandomActor())
                .build();
        var envelope = CommandEnvelope.of(command);
        return envelope;
    }
}
