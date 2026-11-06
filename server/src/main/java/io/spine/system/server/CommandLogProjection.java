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

package io.spine.system.server;

import com.google.protobuf.Empty;
import io.spine.core.Command;
import io.spine.core.CommandContext.Schedule;
import io.spine.core.CommandId;
import io.spine.core.Subscribe;
import io.spine.server.projection.Projection;
import io.spine.system.server.Substituted.Sequence;
import io.spine.system.server.event.CommandAcknowledged;
import io.spine.system.server.event.CommandDispatched;
import io.spine.system.server.event.CommandErrored;
import io.spine.system.server.event.CommandHandled;
import io.spine.system.server.event.CommandReceived;
import io.spine.system.server.event.CommandRejected;
import io.spine.system.server.event.CommandScheduled;
import io.spine.system.server.event.CommandSplit;
import io.spine.system.server.event.CommandTransformed;
import io.spine.system.server.event.TargetAssignedToCommand;

import static io.spine.base.Time.currentTime;

/**
 * The aggregate representing the lifecycle of a command.
 *
 * <p>All the commands in the system (except the commands in the {@code System} bounded context)
 * have an associated {@code CommandLog}.
 */
final class CommandLogProjection
        extends Projection<CommandId, CommandLog, CommandLog.Builder> {

    @Subscribe
    void on(CommandReceived event) {
        timeline().setWhenReceived(currentTime());
        builder().setCommand(event.getPayload());
    }

    @Subscribe
    void on(@SuppressWarnings("unused") CommandAcknowledged event) {
        timeline().setWhenAcknowledged(currentTime());
    }

    @Subscribe
    void on(CommandScheduled event) {
        var updatedCommand = updateSchedule(event.getSchedule());
        timeline().setWhenScheduled(currentTime());
        builder().setCommand(updatedCommand);
    }

    @Subscribe
    void on(@SuppressWarnings("unused") CommandDispatched event) {
        timeline().setWhenDispatched(currentTime());
    }

    @Subscribe
    void on(TargetAssignedToCommand event) {
        var target = event.getTarget();
        timeline().setWhenTargetAssigned(currentTime());
        builder().setTarget(target);
    }

    @Subscribe
    void on(@SuppressWarnings("unused") CommandHandled event) {
        var status = ProcessingStatus.newBuilder()
                .setOk(Empty.getDefaultInstance())
                .build();
        setStatus(status);
    }

    @Subscribe
    void on(HandlerFailedUnexpectedly event) {
        var status = ProcessingStatus.newBuilder()
                .setError(event.getError())
                .build();
        setStatus(status);
    }

    /**
     * Imports the event {@link CommandErrored}.
     *
     * <p>The event is generated if the {@code CommandBus} did not accept the command.
     */
    @Subscribe
    void on(CommandErrored event) {
        var status = ProcessingStatus.newBuilder()
                .setError(event.getError())
                .build();
        setStatus(status);
    }

    /**
     * Imports the event {@link CommandRejected}.
     *
     * <p>The event is generated if the command handler rejected the command.
     */
    @Subscribe
    void on(CommandRejected event) {
        var status = ProcessingStatus.newBuilder()
                .setRejection(event.getRejectionEvent())
                .build();
        setStatus(status);
    }

    @Subscribe
    void on(CommandTransformed event) {
        var substituted = Substituted.newBuilder()
                .setCommand(event.getId())
                .build();
        timeline().setSubstituted(substituted);
    }

    @Subscribe
    void on(CommandSplit event) {
        var sequence = Sequence.newBuilder()
                .addAllItem(event.getProducedList())
                .build();
        var substituted = Substituted.newBuilder()
                .setSequence(sequence)
                .build();
        timeline().setSubstituted(substituted);

    }

    private Command updateSchedule(Schedule schedule) {
        var command = builder().getCommand();
        var updatedContext = command.context()
                .toBuilder()
                .setSchedule(schedule)
                .build();
        var updatedCommand = command.toBuilder()
                .setContext(updatedContext)
                .build();
        return updatedCommand;
    }

    private CommandTimeline.Builder timeline() {
        return builder().getTimelineBuilder();
    }

    private void setStatus(ProcessingStatus status) {
        timeline()
                .setWhenProcessed(currentTime())
                .setHowProcessed(status);
    }
}
