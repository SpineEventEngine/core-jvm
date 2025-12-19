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

package io.spine.server.commandbus;

import io.spine.base.CommandMessage;
import io.spine.base.Identifier;
import io.spine.client.ActorRequestFactory;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandValidationError;
import io.spine.core.Status;
import io.spine.core.TenantId;
import io.spine.grpc.MemoizingObserver;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.commandbus.given.DirectScheduledExecutor;
import io.spine.server.commandbus.given.MemoizingCommandFlowWatcher;
import io.spine.server.event.EventBus;
import io.spine.server.tenant.TenantIndex;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import io.spine.test.commandbus.event.CmdBusProjectCreated;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.model.ModelTests;
import io.spine.validation.ValidationError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.core.CommandValidationError.INVALID_COMMAND;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.commandbus.Given.ACommand.createProject;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base for test suites of {@code CommandBus}.
 */
@SuppressWarnings("ProtectedField") // for brevity of derived tests.
abstract class AbstractCommandBusTestSuite {

    private final boolean multitenant;

    protected ActorRequestFactory requestFactory;

    protected CommandBus commandBus;
    protected EventBus eventBus;
    protected CreateProjectAssignee createProjectAssignee;
    protected MemoizingObserver<Ack> observer;
    protected MemoizingCommandFlowWatcher watcher;
    protected TenantIndex tenantIndex;
    protected SystemWriteSide systemWriteSide;
    protected BoundedContext context;

    /**
     * A public constructor for derived test cases.
     *
     * @param multitenant the multi-tenancy status of the {@code CommandBus} under tests
     */
    AbstractCommandBusTestSuite(boolean multitenant) {
        this.multitenant = multitenant;
    }

    static Command newCommandWithoutContext() {
        var cmd = createProject();
        var invalidCmd = cmd.toBuilder()
                .setContext(CommandContext.getDefaultInstance())
                .buildPartial();
        return invalidCmd;
    }

    static void checkCommandError(Ack sendingResult,
                                  CommandValidationError validationError,
                                  Class<? extends CommandException> exceptionClass,
                                  Command cmd) {
        checkCommandError(sendingResult,
                          validationError,
                          exceptionClass.getCanonicalName(),
                          cmd);
    }

    static void checkCommandError(Ack sendingResult,
                                  CommandValidationError validationError,
                                  String errorType,
                                  Command cmd) {
        var status = sendingResult.getStatus();
        assertEquals(status.getStatusCase(), Status.StatusCase.ERROR);
        var commandId = cmd.getId();
        assertEquals(commandId, unpack(sendingResult.getMessageId()));

        var error = status.getError();
        assertEquals(errorType,
                     error.getType());
        assertEquals(validationError.getNumber(), error.getCode());
        assertFalse(error.getMessage()
                         .isEmpty());
        if (validationError == INVALID_COMMAND) {
            assertTrue(error.hasDetails());
            var details = AnyPacker.unpack(error.getDetails());
            assertThat(details).isInstanceOf(ValidationError.class);
            assertThat(((ValidationError)details).getConstraintViolationList())
                    .isNotEmpty();
        }
    }

    protected static Command newCommandWithoutTenantId() {
        var cmd = createProject();
        var commandBuilder = cmd.toBuilder();
        commandBuilder
                .getContextBuilder()
                .getActorContextBuilder()
                .setTenantId(TenantId.getDefaultInstance());
        return commandBuilder.build();
    }

    protected static Command clearTenantId(Command cmd) {
        var result = cmd.toBuilder();
        result.getContextBuilder()
              .getActorContextBuilder()
              .clearTenantId();
        return result.build();
    }

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();

        ScheduledExecutorService executorService = new DirectScheduledExecutor();
        CommandScheduler scheduler = new ExecutorCommandScheduler(executorService);
        ServerEnvironment.instance()
                         .scheduleCommandsUsing(() -> scheduler);

        context = createContext();

        var contextAccess = context.internalAccess();
        tenantIndex = contextAccess.tenantIndex();
        systemWriteSide = NoOpSystemWriteSide.INSTANCE;

        eventBus = context.eventBus();
        watcher = new MemoizingCommandFlowWatcher();
        commandBus = CommandBus.newBuilder()
                .setMultitenant(this.multitenant)
                .injectContext(context)
                .injectSystem(systemWriteSide)
                .injectTenantIndex(tenantIndex)
                .setWatcher(watcher)
                .build();
        requestFactory =
                multitenant
                ? new TestActorRequestFactory(getClass(), generate())
                : new TestActorRequestFactory(getClass());
        createProjectAssignee = new CreateProjectAssignee();
        contextAccess.registerCommandDispatcher(createProjectAssignee);
        observer = memoizingObserver();
    }

    private BoundedContext createContext() {
        var cls = getClass();
        var name = cls.getSimpleName();
        var builder = multitenant
                      ? BoundedContext.multitenant(name)
                      : BoundedContext.singleTenant(name);
        return builder.build();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    @DisplayName("post commands in bulk")
    void postCommandsInBulk() {
        var first = newCommand();
        var second = newCommand();
        List<Command> commands = newArrayList(first, second);

        // Some derived test suite classes may register the handler in setUp().
        // This prevents the repeating registration (which is an illegal operation).
        commandBus.unregister(createProjectAssignee);
        commandBus.register(createProjectAssignee);

        commandBus.post(commands, memoizingObserver());

        assertTrue(createProjectAssignee.received(first.enclosedMessage()));
        assertTrue(createProjectAssignee.received(second.enclosedMessage()));
        commandBus.unregister(createProjectAssignee);
    }

    protected Command newCommand() {
        return Given.ACommand.createProject();
    }

    protected void checkResult(Command cmd) {
        assertNull(observer.getError());
        assertTrue(observer.isCompleted());
        var ack = observer.firstResponse();
        var messageId = ack.getMessageId();
        assertEquals(cmd.getId(), Identifier.unpack(messageId));
    }

    /**
     * A sample command assignee that tells whether a handling method was invoked.
     */
    final class CreateProjectAssignee extends AbstractAssignee {

        private boolean handlerInvoked = false;
        private final Set<CommandMessage> receivedCommands = newHashSet();

        @Assign
        CmdBusProjectCreated handle(CmdBusCreateProject command, CommandContext ctx) {
            handlerInvoked = true;
            receivedCommands.add(command);
            return CmdBusProjectCreated.newBuilder()
                    .setProjectId(command.getProjectId())
                    .build();
        }

        boolean received(CommandMessage command) {
            return receivedCommands.contains(command);
        }

        boolean wasHandlerInvoked() {
            return handlerInvoked;
        }
    }
}
