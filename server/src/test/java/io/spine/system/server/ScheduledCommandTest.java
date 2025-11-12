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

package io.spine.system.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.Durations;
import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext.Schedule;
import io.spine.core.CommandId;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DefaultRepository;
import io.spine.server.ServerEnvironment;
import io.spine.server.entity.RecordBasedRepository;
import io.spine.system.server.given.command.CompanyAggregate;
import io.spine.system.server.given.schedule.TestCommandScheduler;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.system.server.SystemBoundedContexts.systemOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`ScheduledCommand` should")
class ScheduledCommandTest {

    private static final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(ScheduledCommandTest.class);

    private RecordBasedRepository<CommandId, ScheduledCommand, ScheduledCommandRecord> repository;
    private BoundedContext context;
    private TestCommandScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TestCommandScheduler();
        ServerEnvironment.instance()
                         .scheduleCommandsUsing(() -> scheduler);
        var contextBuilder = BoundedContextBuilder.assumingTests();
        contextBuilder.systemSettings()
                      .enableCommandLog();
        context = contextBuilder.build();
        context.internalAccess()
               .register(DefaultRepository.of(CompanyAggregate.class));
        var system = systemOf(this.context);
        var found = system.internalAccess()
                          .findRepository(ScheduledCommandRecord.class);
        assertTrue(found.isPresent());
        @SuppressWarnings("unchecked") // @Internal API. OK for tests.
        var repository =
                (RecordBasedRepository<CommandId, ScheduledCommand, ScheduledCommandRecord>)
                        found.get();
        this.repository = repository;
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    @DisplayName("be created when a command is scheduled")
    void created() {
        var scheduled = createAndSchedule();
        checkScheduled(scheduled);
    }

    @Test
    @DisplayName("be deleted when a command is dispatched")
    void deleted() {
        var scheduled = createAndSchedule();
        checkScheduled(scheduled);

        scheduler.postScheduled();

        var command = repository.findActive(scheduled.getId());
        assertThat(command).isEmpty();

        var optional = repository.find(scheduled.getId());
        assertTrue(optional.isPresent());
        var deletedCommand = optional.get();
        var flags = deletedCommand.lifecycleFlags();

        assertThat(flags.getDeleted()).isTrue();
        assertThat(flags.getArchived()).isFalse();
    }

    @Test
    @DisplayName("not be created for a non-scheduled command")
    void skipped() {
        var command = createCommand();
        post(command);

        var commandId = command.getId();

        var found = repository.find(commandId);
        assertThat(found).isEmpty();
    }

    private void checkScheduled(Command scheduled) {
        var found = repository.find(scheduled.getId());
        assertTrue(found.isPresent());
        var scheduledCommand = found.get().state();

        assertFalse(isDefault(scheduledCommand.getSchedulingTime()));
        var savedCommand = scheduledCommand.getCommand();
        assertEquals(scheduled, savedCommand);
        scheduler.assertScheduled(savedCommand);
    }

    @CanIgnoreReturnValue
    private Command createAndSchedule() {
        var raw = createCommand();
        var schedule = createSchedule(5_000);
        var scheduled = schedule(raw, schedule);
        post(scheduled);
        return scheduled;
    }

    private static Schedule createSchedule(long delayMillis) {
        var delay = Durations.fromMillis(delayMillis);
        var result = Schedule.newBuilder()
                .setDelay(delay)
                .build();
        return result;
    }

    private static Command schedule(Command command, Schedule schedule) {
        var sourceContext = command.context();
        var newContext = sourceContext.toBuilder()
                .setSchedule(schedule)
                .build();
        var result = command.toBuilder()
                .setContext(newContext)
                .build();
        return result;
    }

    private void post(Command command) {
        context.commandBus()
               .post(command, noOpObserver());
    }

    private static Command createCommand() {
        var commandMessage = createCommandMessage();
        var command = requestFactory.command().create(commandMessage);
        return command;
    }

    private static CommandMessage createCommandMessage() {
        var name = ScheduledCommandTest.class.getSimpleName();
        var result = EstablishCompany.newBuilder()
                .setId(CompanyId.generate())
                .setFinalName(name)
                .build();
        return result;
    }
}
