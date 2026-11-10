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

package io.spine.server.command;

import com.google.protobuf.Empty;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.client.CommandFactory;
import io.spine.core.UserId;
import io.spine.grpc.StreamObservers;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.event.EventFactory;
import io.spine.server.tuple.Pair;
import io.spine.test.command.CmdAssignTask;
import io.spine.test.command.CmdCreateProject;
import io.spine.test.command.CmdCreateTask;
import io.spine.test.command.CmdSetTaskDescription;
import io.spine.test.command.CmdStartTask;
import io.spine.test.command.FirstCmdCreateProject;
import io.spine.test.command.ProjectId;
import io.spine.test.command.Task;
import io.spine.test.command.TaskId;
import io.spine.test.command.event.CmdTaskAdded;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.spine.base.Identifier.newUuid;
import static io.spine.testing.TestValues.random;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`AbstractCommander` should")
class AbstractCommanderTest {

    private final CommandFactory commandFactory = new TestActorRequestFactory(getClass()).command();
    private final EventFactory eventFactory = TestEventFactory.newInstance(getClass());

    private BoundedContext context;
    private CommandInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CommandInterceptor(FirstCmdCreateProject.class,
                                             CmdSetTaskDescription.class,
                                             CmdAssignTask.class,
                                             CmdStartTask.class);
        AbstractCommander commander = new Commendatore();
        context = BoundedContextBuilder
                .assumingTests()
                .addCommandDispatcher(commander)
                .addCommandDispatcher(interceptor)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    @DisplayName("create a command in response to a command")
    void commandOnCommand() {
        var commandMessage = CmdCreateProject.newBuilder()
                .setProjectId(newProjectId())
                .build();
        createCommandAndPost(commandMessage);

        assertTrue(interceptor.contains(FirstCmdCreateProject.class));
    }

    @Test
    @DisplayName("create a command on an event")
    void commandOnEvent() {
        var eventMessage = CmdTaskAdded.newBuilder()
                .setProjectId(newProjectId())
                .setTask(Task.newBuilder()
                             .setTaskId(newTaskId())
                             .build())
                .build();

        createEventAndPost(eventMessage);

        assertTrue(interceptor.contains(CmdSetTaskDescription.class));
    }

    @Test
    @DisplayName("create a pair of commands in response to a command")
    void createCommandPair() {
        postCreateTaskCommand(true);

        assertTrue(interceptor.contains(CmdAssignTask.class));
        assertTrue(interceptor.contains(CmdStartTask.class));
    }

    @Test
    @DisplayName("create a pair of commands with null second command in response to a command")
    void createCommandPairWithNull() {
        postCreateTaskCommand(false);

        assertTrue(interceptor.contains(CmdAssignTask.class));
        assertFalse(interceptor.contains(CmdStartTask.class));
        assertFalse(interceptor.contains(Empty.class));
    }

    /*
     * Test Environment
     *******************************/

    private static ProjectId newProjectId() {
        return ProjectId.newBuilder()
                .setId(newUuid())
                .build();
    }

    private static TaskId newTaskId() {
        return TaskId.newBuilder()
                .setId(random(1, 100))
                .build();
    }

    private static UserId newUserId() {
        return UserId.newBuilder()
                .setValue(newUuid())
                .build();

    }

    private void createCommandAndPost(CommandMessage commandMessage) {
        var command = commandFactory.create(commandMessage);
        context.commandBus()
               .post(command, StreamObservers.noOpObserver());
    }

    private void createEventAndPost(EventMessage eventMessage) {
        var event = eventFactory.createEvent(eventMessage, null);
        context.eventBus()
               .post(event);
    }

    private void postCreateTaskCommand(boolean startTask) {
        var taskId = newTaskId();
        var userId = newUserId();
        var task = Task.newBuilder()
                .setTaskId(taskId)
                .setAssignee(userId)
                .build();
        var commandMessage = CmdCreateTask.newBuilder()
                .setTaskId(taskId)
                .setTask(task)
                .setStart(startTask)
                .build();
        createCommandAndPost(commandMessage);
    }

    /**
     * Test environment class that generates new commands in response to incoming messages.
     */
    private static final class Commendatore extends AbstractCommander {

        @Command
        FirstCmdCreateProject on(CmdCreateProject command) {
            return FirstCmdCreateProject.newBuilder()
                    .setId(command.getProjectId())
                    .build();
        }

        @Command
        CmdSetTaskDescription on(CmdTaskAdded event) {
            return CmdSetTaskDescription.newBuilder()
                    .setTaskId(event.getTask()
                                    .getTaskId())
                    .setDescription("Testing command creation on event")
                    .build();
        }

        @Command
        Pair<CmdAssignTask, Optional<CmdStartTask>> on(CmdCreateTask command) {
            var taskId = command.getTaskId();
            var assignee = command.getTask()
                                  .getAssignee();
            var cmdAssignTask = CmdAssignTask.newBuilder()
                    .setTaskId(taskId)
                    .setAssignee(assignee)
                    .build();
            var cmdStartTask = command.getStart()
                               ? CmdStartTask.newBuilder()
                                       .setTaskId(taskId)
                                       .build()
                               : null;
            return Pair.withNullable(cmdAssignTask, cmdStartTask);
        }
    }
}
