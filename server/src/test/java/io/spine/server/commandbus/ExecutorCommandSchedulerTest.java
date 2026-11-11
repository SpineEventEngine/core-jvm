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

import com.google.common.truth.Correspondence;
import com.google.common.truth.Correspondence.BinaryPredicate;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.spine.client.CommandFactory;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.server.BoundedContext;
import io.spine.server.commandbus.given.CommandHandlerTestEnv.TestCommandAssignee;
import io.spine.server.commandbus.given.DirectScheduledExecutor;
import io.spine.server.commandbus.given.MemoizingCommandFlowWatcher;
import io.spine.server.commandbus.given.ThrowingScheduledExecutor;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenCommandContext;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.protobuf.Durations2.milliseconds;
import static io.spine.server.BoundedContextBuilder.assumingTests;
import static io.spine.server.commandbus.Given.CommandMessage.addTask;
import static io.spine.server.commandbus.Given.CommandMessage.createProjectMessage;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("`ExecutorCommandScheduler` should")
class ExecutorCommandSchedulerTest {

    private static final long DELAY_MS = 1100;

    private static final Duration DELAY = milliseconds(DELAY_MS);

    // Wait a bit longer to ensure the command was processed.
    private static final int WAIT_FOR_PROPAGATION_MS = 300;

    private final CommandFactory commandFactory =
            new TestActorRequestFactory(ExecutorCommandSchedulerTest.class).command();

    private CommandBus commandBus;
    private CommandScheduler scheduler;
    private MemoizingCommandFlowWatcher watcher;
    private CommandContext commandContext;
    private BoundedContext context;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        ScheduledExecutorService executorService = new DirectScheduledExecutor();
        scheduler = new ExecutorCommandScheduler(executorService);
        commandContext = GivenCommandContext.withScheduledDelayOf(DELAY);

        context = assumingTests()
                .addCommandDispatcher(new TestCommandAssignee())
                .build();
        commandBus = context.commandBus();

        watcher = new MemoizingCommandFlowWatcher();
        scheduler.setCommandBus(commandBus);
        scheduler.setWatcher(watcher);
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduler.shutdown();
        context.close();
    }

    @Test
    @DisplayName("schedule command if delay is set")
    void scheduleCmdIfDelaySet() {
        var command = commandFactory.createBasedOnContext(createProjectMessage(), commandContext);
        scheduler.schedule(command);

        assertScheduledExactly(command);
    }

    @Test
    @DisplayName("not schedule command with same ID twice")
    void notScheduleCmdWithSameId() {
        var id = newUuid();

        var firstCommand = commandFactory.createBasedOnContext(createProjectMessage(id),
                                                               commandContext);

        var extraCommand = commandFactory.createBasedOnContext(addTask(id), commandContext)
                .toBuilder()
                .setId(firstCommand.getId())
                .build();

        scheduler.schedule(firstCommand);
        scheduler.schedule(extraCommand);

        assertScheduledExactly(firstCommand);
    }

    @Test
    @MuteLogging
    @DisplayName("continue scheduling commands after error in `post`")
    void recoverFromPostFail() throws Exception {

        // Inject a throwing executor service so the `post` operation fails.
        var service = Executors.newScheduledThreadPool(5);
        var throwingExecutor = new ThrowingScheduledExecutor(service);
        var scheduler = new ExecutorCommandScheduler(throwingExecutor);

        scheduler.setCommandBus(commandBus);
        scheduler.setWatcher(watcher);

        var cmd1 = commandFactory.createBasedOnContext(createProjectMessage(), this.commandContext);
        scheduler.schedule(cmd1);

        waitForCommandProcessed();

        assertThat(throwingExecutor.throwScheduled())
                .isTrue();

        var cmd2 = commandFactory.createBasedOnContext(createProjectMessage(), this.commandContext);
        scheduler.schedule(cmd2);

        assertScheduled(cmd2);

        // Wait for the second command to be "processed" to avoid test output pollution.
        waitForCommandProcessed();
    }

    @Test
    @DisplayName("throw ISE on scheduling attempt when is shutdown")
    void throwExceptionIfIsShutdown() {
        scheduler.shutdown();
        try {
            scheduler.schedule(Given.ACommand.createProject());
        } catch (IllegalStateException expected) {
            // is OK as it is shutdown
            return;
        }
        fail("Must throw an exception as it is shutdown.");
    }

    private void assertScheduledExactly(Command expected) {
        assertThat(watcher.scheduled())
                .hasSize(1);

        var scheduled = watcher.scheduled()
                               .get(0);
        var expectedCmd =
                CommandScheduler.setSchedulingTime(expected, getSchedulingTime(scheduled));
        assertThat(scheduled)
                .isEqualTo(expectedCmd);
    }

    private void assertScheduled(Command expected) {
        BinaryPredicate<Command, Command> isScheduled =
                ExecutorCommandSchedulerTest::isEqualToScheduled;
        var correspondence =
                Correspondence.from(isScheduled, "is the scheduled command");
        assertThat(watcher.scheduled())
                .comparingElementsUsing(correspondence)
                .contains(expected);
    }

    private static boolean isEqualToScheduled(Command scheduled, Command command) {
        var withSchedulingTime =
                CommandScheduler.setSchedulingTime(command, getSchedulingTime(scheduled));
        return withSchedulingTime.equals(scheduled);
    }

    private static Timestamp getSchedulingTime(Command cmd) {
        var time = cmd.getSystemProperties()
                      .getSchedulingTime();
        return time;
    }

    private static void waitForCommandProcessed() throws InterruptedException {
        Thread.sleep(DELAY_MS + WAIT_FOR_PROPAGATION_MS);
    }
}
