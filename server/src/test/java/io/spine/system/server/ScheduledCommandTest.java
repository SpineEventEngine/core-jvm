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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.util.Durations;
import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext.Schedule;
import io.spine.core.CommandId;
import io.spine.server.Application;
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

    /**
     * The application of the {@link #context}, closed after each test.
     */
    private Application application;
    private TestCommandScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TestCommandScheduler();
        var environment = ServerEnvironment.newBuilder()
                .scheduleCommandsUsing(() -> scheduler)
                .build();
        var contextBuilder = BoundedContextBuilder.assumingTests();
        contextBuilder.systemSettings()
                      .enableCommandLog();
        application = Application.newBuilder()
                .setServerEnvironment(environment)
                .add(contextBuilder)
                .build();
        context = application.contexts()
                             .get(0);
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
    void tearDown() {
        application.close();
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
