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
import io.spine.core.Command;
import io.spine.server.BoundedContextBuilder;
import io.spine.test.commandbus.ProjectId;
import io.spine.test.commandbus.command.CmdBusStartProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.Durations2.minutes;
import static io.spine.protobuf.TypeConverter.toMessage;
import static io.spine.server.commandbus.CommandScheduler.setSchedule;
import static io.spine.server.commandbus.Given.ACommand.createProject;
import static io.spine.testing.time.Past.minutesAgo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Command scheduling mechanism should")
@SuppressWarnings("DuplicateStringLiteralInspection") // Common test display names.
class CommandSchedulingTest extends AbstractCommandBusTestSuite {

    CommandSchedulingTest() {
        super(true);
    }

    @Test
    @DisplayName("return `OK`")
    void storeScheduledCommand() {
        commandBus.register(createProjectAssignee);
        var cmd = createProject(/*delay=*/minutes(1));

        commandBus.post(cmd, observer);

        checkResult(cmd);
    }

    @Test
    @DisplayName("schedule command if delay is set")
    void scheduleIfDelayIsSet() {
        commandBus.register(createProjectAssignee);
        var cmd = createProject(/*delay=*/minutes(1));

        commandBus.post(cmd, observer);
    }

    @Test
    @DisplayName("not schedule command if no scheduling options are set")
    void notScheduleWithoutOptions() {
        var assignee = new CreateProjectAssignee();
        assignee.registerWith(BoundedContextBuilder.assumingTests().build());
        commandBus.register(assignee);

        var command = createProject();
        commandBus.post(command, observer);

        checkResult(command);
    }

    @Test
    @DisplayName("post previously scheduled command")
    void postPreviouslyScheduled() {
        commandBus.register(createProjectAssignee);
        var command = createScheduledCommand();

        commandBus.postPreviouslyScheduled(command);

        assertThat(watcher.dispatched())
                .containsExactly(command);
    }

    @Test
    @DisplayName("reject previously scheduled command if no endpoint is found")
    void rejectPreviouslyScheduledWithoutEndpoint() {
        var command = createScheduledCommand();
        assertThrows(IllegalStateException.class,
                     () -> commandBus.postPreviouslyScheduled(command));
    }

    @Nested
    @DisplayName("allow updating")
    class Update {

        @Test
        @DisplayName("scheduling options")
        void schedulingOptions() {
            var cmd = createCommand();
            var schedulingTime = currentTime();
            var delay = minutes(5);

            var cmdUpdated = setSchedule(cmd, delay, schedulingTime);
            var schedule = cmdUpdated.context()
                                     .getSchedule();

            assertEquals(delay, schedule.getDelay());
            assertEquals(schedulingTime, cmdUpdated.getSystemProperties()
                                                   .getSchedulingTime());
        }

        @Test
        @DisplayName("scheduling time")
        void schedulingTime() {
            var cmd = createCommand();
            var schedulingTime = currentTime();

            var cmdUpdated = CommandScheduler.setSchedulingTime(cmd, schedulingTime);

            assertEquals(schedulingTime, cmdUpdated.getSystemProperties()
                                                   .getSchedulingTime());
        }

        private Command createCommand() {
            var id = ProjectId.newBuilder()
                    .setId(newUuid())
                    .build();
            var command = CmdBusStartProject.newBuilder()
                    .setProjectId(id)
                    .build();
            var commandMessage = toMessage(command, CommandMessage.class);
            var cmd = requestFactory.command().create(commandMessage);
            return cmd;
        }
    }

    private static Command createScheduledCommand() {
        var schedulingTime = minutesAgo(3);
        var delayPrimary = minutes(5);
        return setSchedule(createProject(), delayPrimary, schedulingTime);
    }
}
