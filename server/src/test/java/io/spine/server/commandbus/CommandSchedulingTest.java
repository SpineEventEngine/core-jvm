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
