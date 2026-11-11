/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.commandbus;

import io.spine.base.CommandMessage;
import io.spine.base.Identifier;
import io.spine.client.ActorRequestFactory;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.ErrorCase;
import io.spine.core.Status;
import io.spine.core.TenantId;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.Application;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.commandbus.given.DirectScheduledExecutor;
import io.spine.server.commandbus.given.MemoizingCommandFlowWatcher;
import io.spine.server.event.EventBus;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import io.spine.test.commandbus.event.CmdBusProjectCreated;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.commandbus.Given.ACommand.createProject;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    protected SystemWriteSide systemWriteSide;
    protected BoundedContext context;

    /**
     * The application of the {@link #context}, closed after each test.
     */
    private Application application;

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

    static void checkCommandError(Ack sendingResult, ErrorCase errorCase, Command cmd) {
        var status = sendingResult.getStatus();
        assertEquals(Status.StatusCase.ERROR_CASE, status.getStatusCase());
        assertEquals(errorCase, status.getErrorCase());
        var commandId = cmd.getId();
        assertEquals(commandId, unpack(sendingResult.getMessageId()));
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
        var environment = ServerEnvironment.newBuilder()
                .scheduleCommandsUsing(() -> scheduler)
                .build();

        application = createApplication(environment);
        context = application.contexts()
                             .get(0);

        var contextAccess = context.internalAccess();
        systemWriteSide = NoOpSystemWriteSide.INSTANCE;

        eventBus = context.eventBus();
        watcher = new MemoizingCommandFlowWatcher();
        commandBus = CommandBus.newBuilder()
                .setMultitenant(this.multitenant)
                .injectContext(context)
                .injectSystem(systemWriteSide)
                .setWatcher(watcher)
                .build();
        commandBus.registerWith(context);
        requestFactory =
                multitenant
                ? new TestActorRequestFactory(getClass(), generate())
                : new TestActorRequestFactory(getClass());
        createProjectAssignee = new CreateProjectAssignee();
        contextAccess.registerCommandDispatcher(createProjectAssignee);
        observer = memoizingObserver();
    }

    /**
     * Builds the application of the only context of the test.
     */
    private Application createApplication(ServerEnvironment environment) {
        var cls = getClass();
        var name = cls.getSimpleName();
        var builder = multitenant
                      ? BoundedContext.multitenant(name)
                      : BoundedContext.singleTenant(name);
        return Application.newBuilder()
                .setServerEnvironment(environment)
                .add(builder)
                .build();
    }

    /**
     * Closes the application, together with its context and its environment.
     */
    @AfterEach
    void tearDown() {
        application.close();
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
