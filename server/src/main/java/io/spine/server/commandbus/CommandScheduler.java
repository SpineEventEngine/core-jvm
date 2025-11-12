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

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandId;
import io.spine.server.Closeable;
import io.spine.server.bus.BusFilter;
import io.spine.server.type.CommandEnvelope;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.protobuf.util.Timestamps.checkValid;
import static io.spine.base.Time.currentTime;

/**
 * Schedules commands delivering them to the target according to the scheduling options.
 */
public abstract class CommandScheduler implements BusFilter<CommandEnvelope>, Closeable {

    private static final Set<CommandId> scheduledCommandIds = newHashSet();

    private boolean active = true;

    private @Nullable CommandBus commandBus;

    /** Consumes commands when they are scheduled by this instance. */
    private @Nullable CommandFlowWatcher watcher;

    protected CommandScheduler() {
    }

    /**
     * Assigns the {@code CommandBus} to the scheduler during {@code CommandBus}
     * {@linkplain CommandBus.Builder#build() construction}.
     */
    void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Assigns watcher for traching scheduled commands.
     */
    void setWatcher(CommandFlowWatcher watcher) {
        this.watcher = watcher;
    }

    @Override
    public Optional<Ack> filter(CommandEnvelope envelope) {
        var command = envelope.command();
        if (!command.isScheduled()) {
            return letPass();
        }
        schedule(envelope.command());
        return reject(envelope);
    }

    @Override
    public boolean isOpen() {
        return active;
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Schedules a command and delivers it to the target according to the scheduling options.
     *
     * <p>A command with the same ID cannot be scheduled again.
     *
     * @param command
     *         a command to deliver later
     * @throws IllegalStateException
     *         if the scheduler is shut down
     */
    public void schedule(Command command) {
        checkOpen();
        if (isScheduledAlready(command)) {
            return;
        }
        var commandUpdated = setSchedulingTime(command, currentTime());
        doSchedule(commandUpdated);
        rememberAsScheduled(commandUpdated);

        var updatedCommandEnvelope = CommandEnvelope.of(commandUpdated);
        watcher().onScheduled(updatedCommandEnvelope);
    }

    /**
     * Obtains {@code CommandBus} associated with this scheduler.
     *
     * @throws IllegalStateException
     *         if {@code CommandBus} was not {@linkplain #setCommandBus(CommandBus) set} prior
     *         to calling this method
     */
    protected CommandBus commandBus() {
        checkState(commandBus != null, "`CommandBus` is not set.");
        return commandBus;
    }

    private CommandFlowWatcher watcher() {
        checkState(
                watcher != null,
                "`%s` is not assigned. Please call `setWatcher()`.",
                CommandFlowWatcher.class.getName()
        );
        return watcher;
    }

    /**
     * Schedules a command and delivers it to the target according to
     * the scheduling options set to a context.
     *
     * @param command
     *         a command to deliver later
     * @see #post(Command)
     */
    protected abstract void doSchedule(Command command);

    /**
     * Delivers a scheduled command to a target.
     *
     * @param command
     *         a command to deliver
     */
    protected void post(Command command) {
        commandBus().postPreviouslyScheduled(command);
    }

    private static boolean isScheduledAlready(Command command) {
        var id = command.getId();
        var isScheduledAlready = scheduledCommandIds.contains(id);
        return isScheduledAlready;
    }

    private static void rememberAsScheduled(Command command) {
        var id = command.getId();
        scheduledCommandIds.add(id);
    }

    /**
     * Initiates an orderly shutdown in which previously scheduled commands will be delivered later,
     * but no new commands will be accepted.
     *
     * <p>Invocation has no effect if the scheduler is already shut down.
     */
    public void shutdown() {
        active = false;
    }

    /**
     * Sets a new scheduling time in the {@linkplain CommandContext.Schedule context}
     * of the passed command.
     *
     * @param command
     *         a command to update
     * @param schedulingTime
     *         the time when the command was scheduled by the {@code CommandScheduler}
     * @return an updated command
     */
    static Command setSchedulingTime(Command command, Timestamp schedulingTime) {
        checkNotNull(command);
        checkNotNull(schedulingTime);

        var delay = command.getContext()
                           .getSchedule()
                           .getDelay();
        var result = setSchedule(command, delay, schedulingTime);
        return result;
    }

    /**
     * Updates {@linkplain CommandContext.Schedule command schedule}.
     *
     * @param command
     *         a command to update
     * @param delay
     *         a {@linkplain CommandContext.Schedule#getDelay() delay} to set
     * @param schedulingTime
     *         the time when the command was scheduled by the {@code CommandScheduler}
     * @return an updated command
     */
    static Command setSchedule(Command command, Duration delay, Timestamp schedulingTime) {
        checkNotNull(command);
        checkNotNull(delay);
        checkNotNull(schedulingTime);
        checkValid(schedulingTime);

        var context = command.getContext();
        var scheduleUpdated = context.getSchedule()
                .toBuilder()
                .setDelay(delay)
                .build();
        var contextUpdated = context.toBuilder()
                .setSchedule(scheduleUpdated)
                .build();

        var sysProps = command.getSystemProperties()
                .toBuilder()
                .setSchedulingTime(schedulingTime)
                .build();
        var result = command.toBuilder()
                .setContext(contextUpdated)
                .setSystemProperties(sysProps)
                .build();
        return result;
    }
}
