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

package io.spine.server.event.store;

import com.google.common.collect.ImmutableSet;
import io.spine.base.Time;
import io.spine.core.ActorContext;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.Origin;
import io.spine.core.TenantId;
import io.spine.grpc.MemoizingObserver;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.event.EventFilter;
import io.spine.server.event.EventStore;
import io.spine.server.event.EventStreamQuery;
import io.spine.server.event.given.EventStoreTestEnv.ResponseObserver;
import io.spine.server.type.given.GivenEvent;
import io.spine.test.event.TaskAdded;
import io.spine.testing.SlowTest;
import io.spine.testing.TestValues;
import io.spine.type.TypeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static com.google.protobuf.util.Timestamps.add;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.protobuf.Durations2.seconds;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.server.event.given.EventStoreTestEnv.assertDone;
import static io.spine.server.event.given.EventStoreTestEnv.projectCreated;
import static io.spine.server.event.given.EventStoreTestEnv.taskAdded;
import static io.spine.testing.TestValues.random;
import static io.spine.testing.core.given.GivenEnrichment.withOneAttribute;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EventStore` should")
public class DefaultEventStoreTest {

    private BoundedContext context;
    private EventStore eventStore;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(64);
        context = BoundedContextBuilder.assumingTests().build();
        eventStore = context.eventBus().eventStore();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
        executor.shutdownNow();
    }

    @Nested
    @DisplayName("read events by")
    class ReadEventsBy {

        @Test
        @DisplayName("time bounds")
        void timeBounds() {
            var delta = seconds(111);
            var present = currentTime();
            var past = subtract(present, delta);
            var future = add(present, delta);

            var eventInPast = projectCreated(past);
            var eventInPresent = projectCreated(present);
            var eventInFuture = projectCreated(future);

            eventStore.append(eventInPast);
            eventStore.append(eventInPresent);
            eventStore.append(eventInFuture);

            var query = EventStreamQuery.newBuilder()
                    .setAfter(past)
                    .setBefore(future)
                    .build();
            var done = new AtomicBoolean(false);
            var observer = new ResponseObserver(done);
            eventStore.read(query, observer);
            Collection<Event> resultEvents = observer.getEvents();

            assertDone(done);
            assertThat(resultEvents).hasSize(1);
            var event = resultEvents.iterator().next();
            assertEquals(eventInPresent, event);
        }

        @Test
        @DisplayName("time bounds and limit")
        void timeBoundsAndLimit() {
            var delta = seconds(222);
            var present = currentTime();
            var past = subtract(present, delta);
            var longPast = subtract(past, delta);
            var future = add(present, delta);

            var eventInPast = projectCreated(past);
            var eventInPresent = projectCreated(present);
            var eventInFuture = projectCreated(future);

            eventStore.append(eventInPast);
            eventStore.append(eventInPresent);
            eventStore.append(eventInFuture);

            var expectedSize = 1;
            var query = EventStreamQuery.newBuilder()
                    .setAfter(longPast)
                    .setBefore(future)
                    .setLimit(limitOf(expectedSize))
                    .build();
            var done = new AtomicBoolean(false);
            var observer = new ResponseObserver(done);
            eventStore.read(query, observer);
            Collection<Event> resultEvents = observer.getEvents();

            assertDone(done);
            assertThat(resultEvents).hasSize(expectedSize);
            var actualEvent = resultEvents.iterator().next();
            assertEquals(eventInPast, actualEvent);
        }

        @Test
        @DisplayName("type")
        void type() {
            var now = currentTime();

            var taskAdded1 = taskAdded(now);
            var projectCreated = projectCreated(now);
            var taskAdded2 = taskAdded(now);

            eventStore.append(taskAdded1);
            eventStore.append(projectCreated);
            eventStore.append(taskAdded2);

            var taskAddedType = EventFilter.newBuilder()
                    .setEventType(TypeName.of(TaskAdded.class)
                                          .value())
                    .build();
            var query = EventStreamQuery.newBuilder()
                    .addFilter(taskAddedType)
                    .build();
            var done = new AtomicBoolean(false);
            var observer = new ResponseObserver(done);
            eventStore.read(query, observer);
            assertDone(done);

            var assertResultEvents = assertThat(observer.getEvents());
            assertResultEvents.hasSize(2);
            assertResultEvents.containsExactly(taskAdded1, taskAdded2);
        }

        @Test
        @DisplayName("type and limit")
        void typeAndLimit() {
            var now = currentTime();
            var future = add(now, seconds(1));

            var taskAdded1 = taskAdded(now);
            var projectCreated = projectCreated(now);
            var taskAdded2 = taskAdded(future);

            eventStore.append(taskAdded1);
            eventStore.append(projectCreated);
            eventStore.append(taskAdded2);

            var taskAddedType = EventFilter.newBuilder()
                    .setEventType(TypeName.of(TaskAdded.class)
                                          .value())
                    .build();
            var expectedSize = 1;
            var query = EventStreamQuery.newBuilder()
                    .addFilter(taskAddedType)
                    .setLimit(limitOf(expectedSize))
                    .build();
            var done = new AtomicBoolean(false);
            var observer = new ResponseObserver(done);
            eventStore.read(query, observer);
            assertDone(done);

            var assertResultEvents = assertThat(observer.getEvents());
            assertResultEvents.hasSize(1);
            assertResultEvents.containsExactly(taskAdded1);
        }

        @Test
        @DisplayName("time bounds and type")
        void timeBoundsAndType() {
            var delta = seconds(111);
            var present = currentTime();
            var past = subtract(present, delta);
            var future = add(present, delta);

            var eventInPast = taskAdded(past);
            var eventInPresent = projectCreated(present);
            var eventInFuture = taskAdded(future);

            eventStore.append(eventInPast);
            eventStore.append(eventInPresent);
            eventStore.append(eventInFuture);

            var taskAddedType = EventFilter.newBuilder()
                    .setEventType(TypeName.of(TaskAdded.class)
                                          .value())
                    .build();
            var query = EventStreamQuery.newBuilder()
                    .setAfter(past)
                    .addFilter(taskAddedType)
                    .build();
            var done = new AtomicBoolean(false);
            var observer = new ResponseObserver(done);
            eventStore.read(query, observer);
            assertDone(done);

            var assertResultEvents = assertThat(observer.getEvents());
            assertResultEvents.hasSize(1);
            assertResultEvents.containsExactly(eventInFuture);
        }

        @Test
        @DisplayName("time bounds, type and limit")
        void timeBoundsTypeAndFuture() {
            var delta = seconds(111);
            var present = currentTime();
            var past = subtract(present, delta);
            var future = add(present, delta);
            var distantFuture = add(future, delta);

            var eventInPast = taskAdded(past);
            var eventInPresent = projectCreated(present);
            var eventInFuture = taskAdded(future);
            var eventInDistantFuture = taskAdded(distantFuture);

            eventStore.append(eventInPast);
            eventStore.append(eventInPresent);
            eventStore.append(eventInFuture);
            eventStore.append(eventInDistantFuture);

            var taskAddedType = EventFilter.newBuilder()
                    .setEventType(TypeName.of(TaskAdded.class)
                                          .value())
                    .build();
            var query = EventStreamQuery.newBuilder()
                    .setAfter(past)
                    .addFilter(taskAddedType)
                    .setLimit(limitOf(1))
                    .build();
            var done = new AtomicBoolean(false);
            var observer = new ResponseObserver(done);
            eventStore.read(query, observer);
            assertDone(done);

            var assertResultEvents = assertThat(observer.getEvents());
            assertResultEvents.hasSize(1);
            assertResultEvents.containsExactly(eventInFuture);
        }

        private EventStreamQuery.Limit limitOf(int value) {
            return EventStreamQuery.Limit.newBuilder()
                    .setValue(value)
                    .build();
        }
    }

    @Test
    @DisplayName("do nothing when appending empty `Iterable`")
    void processEmptyIterable() {
        eventStore.appendAll(Collections.emptySet());
    }

    @Test
    @DisplayName("fail to store events of different tenants in single operation")
    void rejectEventsFromDifferentTenants() {
        var firstTenantId = TenantId.newBuilder()
                .setValue("abc")
                .buildPartial();
        var secondTenantId = TenantId.newBuilder()
                .setValue("xyz")
                .buildPartial();
        var firstTenantActor = ActorContext.newBuilder()
                .setTenantId(firstTenantId)
                .buildPartial();
        var secondTenantActor = ActorContext.newBuilder()
                .setTenantId(secondTenantId)
                .buildPartial();
        var firstTenantOrigin = Origin.newBuilder()
                .setActorContext(firstTenantActor)
                .buildPartial();
        var secondTenantOrigin = Origin.newBuilder()
                .setActorContext(secondTenantActor)
                .buildPartial();
        var firstTenantContext = EventContext.newBuilder()
                .setPastMessage(firstTenantOrigin)
                .buildPartial();
        var secondTenantContext = EventContext.newBuilder()
                .setPastMessage(secondTenantOrigin)
                .buildPartial();
        var firstTenantEvent = Event.newBuilder()
                .setContext(firstTenantContext)
                .buildPartial();
        var secondTenantEvent = Event.newBuilder()
                .setContext(secondTenantContext)
                .buildPartial();
        Collection<Event> event = ImmutableSet.of(firstTenantEvent, secondTenantEvent);

        assertThrows(IllegalArgumentException.class, () -> eventStore.appendAll(event));
    }

    @SlowTest
    @Test
    @DisplayName("be able to store events in parallel")
    void storeInParallel() {
        var eventCount = 10_000;
        var futures =
                Stream.generate(GivenEvent::arbitrary)
                      .limit(eventCount)
                      .map(event -> runAsync(() -> waitAndStore(event), executor))
                      .toArray(CompletableFuture[]::new);
        var allFutures = allOf(futures);
        allFutures.join();
        MemoizingObserver<Event> observer = memoizingObserver();
        eventStore.read(EventStreamQuery.getDefaultInstance(), observer);
        assertThat(observer.isCompleted())
                .isTrue();
        assertThat(observer.responses())
                .hasSize(eventCount);
    }

    private void waitAndStore(Event event) {
        sleepUninterruptibly(ofMillis(random(20, 40)));
        eventStore.append(event);
    }

    @Nested
    @DisplayName("not store enrichment for")
    class NotStoreEnrichmentFor {

        @Test
        @DisplayName("`EventContext`")
        void eventContext() {
            var event = projectCreated(Time.currentTime());
            var enriched = event.toBuilder()
                    .setContext(event.context()
                                        .toBuilder()
                                        .setEnrichment(withOneAttribute()))
                    .build();
            eventStore.append(enriched);
            MemoizingObserver<Event> observer = memoizingObserver();
            eventStore.read(EventStreamQuery.getDefaultInstance(), observer);
            var context = observer.responses().get(0).context();
            assertTrue(isDefault(context.getEnrichment()));
        }

        @SuppressWarnings("deprecation")
            // Enrichment cleanup still checks for the deprecated fields, so keep the test.
        @Test
        @DisplayName("origin of `EventContext` type")
        void eventContextOrigin() {
            var event = projectCreated(Time.currentTime());
            var pastMessage = event.context().getPastMessage();
            var originContext = EventContext.newBuilder()
                    .setEnrichment(withOneAttribute())
                    .setPastMessage(pastMessage)
                    .setTimestamp(event.context()
                                       .getTimestamp())
                    .setProducerId(AnyPacker.pack(TestValues.newUuidValue()))
                    .build();
            var enriched = event.toBuilder()
                    .setContext(event.getContext()
                                        .toBuilder()
                                        .setEventContext(originContext))
                    .build();
            eventStore.append(enriched);
            MemoizingObserver<Event> observer = memoizingObserver();
            eventStore.read(EventStreamQuery.getDefaultInstance(), observer);
            var loadedOriginContext = observer.responses()
                                              .get(0)
                                              .context()
                                              .getEventContext();
            assertTrue(isDefault(loadedOriginContext.getEnrichment()));
        }
    }
}
