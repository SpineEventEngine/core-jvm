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

package io.spine.server.event;

import io.grpc.stub.StreamObserver;
import io.spine.core.Ack;
import io.spine.grpc.StreamObservers;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.event.given.bus.BareDispatcher;
import io.spine.server.event.given.bus.EBExternalTaskAddedSubscriber;
import io.spine.server.event.given.bus.EBProjectArchivedSubscriber;
import io.spine.server.event.given.bus.EBProjectCreatedNoOpSubscriber;
import io.spine.server.event.given.bus.EBTaskAddedNoOpSubscriber;
import io.spine.server.event.given.bus.GivenEvent;
import io.spine.server.event.given.bus.ProjectAggregate;
import io.spine.server.event.given.bus.RememberingSubscriber;
import io.spine.server.event.given.bus.TaskCreatedFilter;
import io.spine.server.type.EventClass;
import io.spine.test.event.EBTaskAdded;
import io.spine.test.event.ProjectCreated;
import io.spine.test.event.ProjectId;
import io.spine.test.event.Task;
import io.spine.test.event.TaskId;
import io.spine.testdata.Sample;
import io.spine.testing.SlowTest;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestEventFactory;
import io.spine.validation.Validated;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.Executors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.BoundedContextBuilder.assumingTests;
import static io.spine.server.event.given.bus.EventBusTestEnv.addTasks;
import static io.spine.server.event.given.bus.EventBusTestEnv.command;
import static io.spine.server.event.given.bus.EventBusTestEnv.createProject;
import static io.spine.server.event.given.bus.EventBusTestEnv.invalidArchiveProject;
import static io.spine.server.event.given.bus.EventBusTestEnv.newTask;
import static io.spine.server.event.given.bus.EventBusTestEnv.readEvents;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EventBus` should")
class EventBusTest {

    private TestEventFactory eventFactory;
    private EventBus eventBus;
    private CommandBus commandBus;
    private BoundedContext context;

    private void setUp(@Nullable EventEnricher enricher) {
        this.eventFactory = TestEventFactory.newInstance(EventBusTest.class);
        var builder = assumingTests(true)
                .addEventFilter(new TaskCreatedFilter())
                .add(ProjectAggregate.class);
        if (enricher != null) {
            builder.enrichEventsUsing(enricher);
        }
        this.context = builder.build();
        this.commandBus = context.commandBus();
        this.eventBus = context.eventBus();
    }

    @BeforeEach
    void setUp() {
        setUp(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
        ServerEnvironment.instance()
                         .reset();
    }

    @Test
    @DisplayName("have builder")
    @SuppressWarnings("DuplicateStringLiteralInspection") // Common test case.
    void haveBuilder() {
        assertNotNull(EventBus.newBuilder());
    }

    @Test
    @DisplayName("reject object which has no subscriber methods")
    void rejectEmptySubscriber() {

        // Pass just String instance.
        assertThrows(IllegalArgumentException.class,
                     () -> eventBus.register(new AbstractEventSubscriber() {}));
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("event subscriber")
        void eventSubscriber() {
            AbstractEventSubscriber subscriberOne = new RememberingSubscriber();
            AbstractEventSubscriber subscriberTwo = new RememberingSubscriber();

            eventBus.register(subscriberOne);
            eventBus.register(subscriberTwo);

            var eventClass = EventClass.from(ProjectCreated.class);
            assertTrue(eventBus.hasDispatchers(eventClass));

            Collection<? extends EventDispatcher> dispatchers =
                    eventBus.dispatchersOf(eventClass);
            assertTrue(dispatchers.contains(subscriberOne));
            assertTrue(dispatchers.contains(subscriberTwo));
        }

        @Test
        @DisplayName("event dispatcher")
        void eventDispatcher() {
            EventDispatcher dispatcher = new BareDispatcher();

            eventBus.register(dispatcher);

            assertTrue(eventBus.dispatchersOf(EventClass.from(ProjectCreated.class))
                               .contains(dispatcher));
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("event subscriber")
        void eventSubscriber() {
            AbstractEventSubscriber subscriberOne = new RememberingSubscriber();
            AbstractEventSubscriber subscriberTwo = new RememberingSubscriber();
            eventBus.register(subscriberOne);
            eventBus.register(subscriberTwo);
            var eventClass = EventClass.from(ProjectCreated.class);

            eventBus.unregister(subscriberOne);

            // Check that the 2nd subscriber with the same event subscriber method remains
            // after the 1st subscriber unregisters.
            Collection<? extends EventDispatcher> subscribers =
                    eventBus.dispatchersOf(eventClass);
            assertFalse(subscribers.contains(subscriberOne));
            assertTrue(subscribers.contains(subscriberTwo));

            // Check that after 2nd subscriber us unregisters he's no longer in
            eventBus.unregister(subscriberTwo);

            assertFalse(eventBus.dispatchersOf(eventClass)
                                .contains(subscriberTwo));
        }

        @Test
        @DisplayName("event dispatcher")
        void eventDispatcher() {
            EventDispatcher dispatcherOne = new BareDispatcher();
            EventDispatcher dispatcherTwo = new BareDispatcher();
            var eventClass = EventClass.from(ProjectCreated.class);
            eventBus.register(dispatcherOne);
            eventBus.register(dispatcherTwo);

            eventBus.unregister(dispatcherOne);
            var dispatchers = eventBus.dispatchersOf(eventClass);

            // Check we don't have 1st dispatcher, but have 2nd.
            assertFalse(dispatchers.contains(dispatcherOne));
            assertTrue(dispatchers.contains(dispatcherTwo));

            eventBus.unregister(dispatcherTwo);
            assertFalse(eventBus.dispatchersOf(eventClass)
                                .contains(dispatcherTwo));
        }
    }

    @Nested
    @DisplayName("call")
    class Call {

        @Test
        @DisplayName("event subscriber")
        void eventSubscriber() {
            var subscriber = new RememberingSubscriber();
            var event = GivenEvent.projectCreated();
            eventBus.register(subscriber);

            eventBus.post(event);

            // Exclude event ID from comparison.
            assertThat(subscriber.getEventMessage())
                    .isEqualTo(event.enclosedMessage());
            assertThat(subscriber.getEventContext())
                    .isEqualTo(event.context());
        }

        @Test
        @DisplayName("event dispatcher")
        void eventDispatcher() {
            var dispatcher = new BareDispatcher();

            eventBus.register(dispatcher);

            eventBus.post(GivenEvent.projectCreated());

            assertThat(dispatcher.isDispatchCalled())
                    .isTrue();
        }
    }


    @Nested
    @DisplayName("when closed")
    class ClosingBus {

        private EventBus eventBus;

        @BeforeEach
        void setUp() throws Exception {
            var context = assumingTests()
                    .addEventDispatcher(new BareDispatcher())
                    .addEventDispatcher(new RememberingSubscriber())
                    .build();

            eventBus = context.eventBus();
            eventBus.close();
        }

        @Test
        @DisplayName("unregister dispatchers")
        void unregisterDispatchers() {
            var eventClass = EventClass.from(ProjectCreated.class);
            assertThat(eventBus.dispatchersOf(eventClass))
                    .isEmpty();
        }

        @Test
        @DisplayName("close `EventStore`")
        void unregisterRegistriesOnClose() {
            assertFalse(eventBus.eventStore()
                                .isOpen());
        }
    }

    @Test
    @DisplayName("create validator once")
    void createValidatorOnce() {
        var validator = eventBus.validator();
        assertNotNull(validator);
        assertSame(validator, eventBus.validator());
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("event")
        void event() {
            var command = command(createProject());
            eventBus.register(new EBProjectCreatedNoOpSubscriber());
            commandBus.post(command, StreamObservers.noOpObserver());

            var events = readEvents(eventBus);
            assertThat(events).hasSize(1);
        }

        @Test
        @DisplayName("dead event")
        void deadEvent() {
            var command = command(createProject());

            commandBus.post(command, StreamObservers.noOpObserver());

            var events = readEvents(eventBus);
            assertThat(events).hasSize(1);
        }

        /**
         * Ensures that events are stored when all of them pass the filters.
         *
         * <p> To filter the {@link EBTaskAdded} events the {@linkplain EventBus} has a custom
         * filter. The {@link TaskCreatedFilter} filters out {@link EBTaskAdded} events with
         * {@link Task#getDone()} set to {@code true}.
         *
         * <p> The {@link EBTaskAddedNoOpSubscriber} is registered so that the event would not get
         * filtered out by the {@link io.spine.server.bus.DeadMessageFilter}.
         */
        @Test
        @DisplayName("multiple events")
        void multipleEvents() {
            eventBus.register(new EBTaskAddedNoOpSubscriber());
            var command = command(addTasks(newTask(false), newTask(false), newTask(false)));

            commandBus.post(command, StreamObservers.noOpObserver());

            var storedEvents = readEvents(eventBus);
            assertThat(storedEvents).hasSize(3);
        }
    }

    @Nested
    @DisplayName("not store")
    class NotStore {

        @Test
        @DisplayName("invalid event")
        void invalidEvent() {
            var command = command(invalidArchiveProject());
            eventBus.register(new EBProjectArchivedSubscriber());

            commandBus.post(command, StreamObservers.noOpObserver());

            var events = readEvents(eventBus);
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("invalid dead event")
        void invalidDeadEvent() {
            var command = command(invalidArchiveProject());

            commandBus.post(command, StreamObservers.noOpObserver());

            var events = readEvents(eventBus);
            assertThat(events).isEmpty();
        }

        /**
         * Ensures that events are not stored when none of them pass the filters.
         *
         * <p>To filter the {@link EBTaskAdded} events the {@linkplain EventBus} has a custom
         * filter. The {@link TaskCreatedFilter} filters out {@link EBTaskAdded} events with
         * {@link Task#getDone()} set to {@code true}.
         *
         * <p>The {@link EBTaskAddedNoOpSubscriber} is registered so that the event would not get
         * filtered out by the {@link io.spine.server.bus.DeadMessageFilter}.
         */
        @Test
        @DisplayName("multiple events failing filters")
        void multipleEventsFailingFilters() {
            eventBus.register(new EBTaskAddedNoOpSubscriber());
            var command = command(addTasks(newTask(true), newTask(true), newTask(true)));

            commandBus.post(command, StreamObservers.noOpObserver());

            var storedEvents = readEvents(eventBus);
            assertThat(storedEvents).isEmpty();
        }
    }

    /**
     * Ensures that events which pass filters and the ones that don’t are treated independently when
     * sent in batch.
     *
     * <p> To filter the {@link EBTaskAdded} events the {@linkplain EventBus} has a custom filter.
     * The {@link TaskCreatedFilter} filters out {@link EBTaskAdded} events with
     * {@link Task#getDone()} set to {@code true}.
     *
     * <p> The {@link EBTaskAddedNoOpSubscriber} is registered so that the event would not get
     * filtered out by the {@link io.spine.server.bus.DeadMessageFilter}.
     */
    @Test
    @DisplayName("store only events passing filters")
    void filterOutFailingEvents() {
        eventBus.register(new EBTaskAddedNoOpSubscriber());
        var command = command(addTasks(newTask(false), newTask(true), newTask(false),
                                       newTask(true), newTask(true)));

        commandBus.post(command, StreamObservers.noOpObserver());

        var storedEvents = readEvents(eventBus);
        assertThat(storedEvents).hasSize(2);

        for (var event : storedEvents) {
            var contents = unpack(event.getMessage(), EBTaskAdded.class);
            var task = contents.getTask();
            assertFalse(task.getDone());
        }
    }

    @Test
    @DisplayName("not dispatch domestic event to external handler")
    @MuteLogging
    void domesticEventToExternalMethod() {
        var subscriber = new EBExternalTaskAddedSubscriber();
        eventBus.register(subscriber);

        var projectId = Sample.messageOfType(ProjectId.class);
        var taskId = Sample.messageOfType(TaskId.class);
        var task = Task.newBuilder()
                .setTaskId(taskId)
                .setTitle("Foo")
                .setDescription("Foo bar")
                .build();
        var eventMessage = EBTaskAdded.newBuilder()
                .setProjectId(projectId)
                .setTask(task)
                .build();
        var event = eventFactory.createEvent(eventMessage);
        eventBus.post(event, StreamObservers.noOpObserver());
        assertThat(EBExternalTaskAddedSubscriber.taskAddedEvent).isNull();
    }

    /**
     * Tests the concurrent access to the {@linkplain io.spine.server.bus.BusFilter bus filters}.
     *
     * <p>The {@linkplain io.spine.server.bus.FilterChain filter chain} is a queue of the filters
     * which are sequentially applied to the posted message. The first {@code Bus.post()} call
     * invokes the filters lazy initialization. In the concurrent environment (which is natural for
     * a {@link io.spine.server.bus.Bus Bus}), the initialization may be performed multiple times.
     * Thus, some unexpected issues may appear when accessing the non-synchronously initialized
     * filter chain.
     *
     * <p>To make sure that the chain works fine (i.e. produces no exceptions), we invoke the
     * initialization multiple times from several threads.
     */
    @Test
    @SlowTest
    @DisplayName("store filters as expected if initialized concurrently")
    @SuppressWarnings("MethodWithMultipleLoops") /* OK for such test case. */
    void storeFiltersInConcurrentEnv() {
        var threadCount = 50;
        @Validated DonationMade eventMessage = DonationMade.newBuilder()
                .setUsdsDonated(3.14)
                .build();
        var event = eventFactory.createEvent(eventMessage);
        var executor = Executors.newFixedThreadPool(threadCount);
        // Catch non-easily reproducible bugs.
        for (var i = 0; i < 300; i++) {
            var context = BoundedContextBuilder.assumingTests().build();
            var eventBus = context.eventBus();
            StreamObserver<Ack> observer = StreamObservers.noOpObserver();
            for (var j = 0; j < threadCount; j++) {
                executor.execute(() -> {
                    if (eventBus.isOpen()) {
                        eventBus.post(event, observer);
                    }
                });
            }
            // Let the system destroy all the native threads, clean up, etc.
            sleepUninterruptibly(100, MILLISECONDS);
        }
        executor.shutdownNow();
    }
}
