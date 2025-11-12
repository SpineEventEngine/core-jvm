/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.system.server.given.schedule;

import com.google.protobuf.Any;
import io.spine.core.Command;
import io.spine.server.commandbus.CommandScheduler;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestCommandScheduler extends CommandScheduler {

    private final Set<Command> scheduledCommands = newHashSet();

    @Override
    protected void doSchedule(Command command) {
        scheduledCommands.add(command);
    }

    public void assertScheduled(Command command) {
        // System properties are modified by the framework.
        var found = scheduledCommands.stream()
                .map(cmd -> cmd.toBuilder()
                        .clearSystemProperties()
                        .build())
                .anyMatch(command::equals);
        var scheduledCommands = this.scheduledCommands.stream()
                .map(Command::getMessage)
                .map(Any::getTypeUrl)
                .collect(joining(", "));
        assertTrue(found,
                   format("Command is not scheduled. " +
                                  "%n expected command:%n%s%nActually scheduled:%n%s",
                          command, scheduledCommands));
    }

    public void postScheduled() {
        scheduledCommands.forEach(this::post);
        scheduledCommands.clear();
    }
}
