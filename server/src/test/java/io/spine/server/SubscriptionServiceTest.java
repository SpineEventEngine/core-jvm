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

package io.spine.server;

import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.base.EntityState;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionId;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Targets;
import io.spine.client.Topic;
import io.spine.client.TopicFactory;
import io.spine.core.Response;
import io.spine.grpc.MemoizingObserver;
import io.spine.grpc.StreamObservers;
import io.spine.server.Given.AggProjectCreatedReactor;
import io.spine.server.Given.ProjectAggregateRepository;
import io.spine.server.Given.ReportSender;
import io.spine.server.stand.InvalidSubscriptionException;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.event.AggOwnerNotified;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggTaskAdded;
import io.spine.test.commandservice.customer.Customer;
import io.spine.test.projection.BankAccount;
import io.spine.test.subscriptionservice.event.ReportSent;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.logging.LoggingTest;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.model.ModelTests;
import io.spine.type.UnpublishedLanguageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.logging.Level;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.Given.CommandMessage.createProject;
import static io.spine.server.Given.CommandMessage.sendReport;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`SubscriptionService` should")
class SubscriptionServiceTest {

    private final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(SubscriptionServiceTest.class);

    private BoundedContext context;
    private SubscriptionService subscriptionService;

    /** The observer for creating a subscription. */
    private MemoizingObserver<Subscription> observer;

    /** The observer for activating a subscription. */
    private MemoizingObserver<SubscriptionUpdate> activationObserver;

    /** The observer for cancelling a subscription. */
    private MemoizingObserver<Response> cancellationObserver;

    @BeforeEach
    @SuppressWarnings("resource")  /* Fine for tests. */
    void setUp() {
        ModelTests.dropAllModels();
        ServerEnvironment.instance()
                         .reset();
        context = BoundedContextBuilder
                .assumingTests()
                .add(new ProjectAggregateRepository())
                .addEventDispatcher(new AggProjectCreatedReactor())
                .addCommandDispatcher(new ReportSender())
                .build();
        subscriptionService = SubscriptionService.newBuilder()
                .add(context)
                .add(randomCtx())
                .add(randomCtx())
                .build();
        observer = new MemoizingObserver<>();
        activationObserver = new MemoizingObserver<>();
        cancellationObserver = new MemoizingObserver<>();
    }

    private static BoundedContext randomCtx() {
        return BoundedContext.singleTenant(newUuid())
                             .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    /*
     * Tests of work with multiple Bounded Contexts
     * ---------------------------------------------
     */

    /** Creates a new multi-tenant BoundedContext with the passed name. */
    private static BoundedContext ctx(String name) {
        return BoundedContext.multitenant(name)
                             .build();
    }

    @Nested
    @DisplayName("initialize properly with")
    class InitProperlyWith {

        @Test
        @DisplayName("one bounded context")
        void oneBc() {
            var oneContext = ctx("One");

            var builder = SubscriptionService.newBuilder().add(oneContext);

            var subscriptionService = builder.build();
            assertNotNull(subscriptionService);

            Set<BoundedContext> contexts = builder.contexts();
            assertThat(contexts).hasSize(1);
            assertTrue(contexts.contains(oneContext));
        }

        @Test
        @DisplayName("several bounded contexts")
        void severalBcs() {
            var bc1 = ctx("First");
            var bc2 = ctx("Second");
            var bc3 = ctx("Third");

            var builder = SubscriptionService.newBuilder()
                    .add(bc1)
                    .add(bc2)
                    .add(bc3);
            var service = builder.build();
            assertNotNull(service);

            Set<BoundedContext> contexts = builder.contexts();
            assertThat(contexts).containsExactly(bc1, bc2, bc3);
        }
    }

    @Test
    @DisplayName("be able to remove bounded context from builder")
    void removeBcFromBuilder() {
        var bc1 = ctx("Removed");
        var bc2 = ctx("Also removed");
        var bc3 = ctx("The one to stay");

        var builder = SubscriptionService.newBuilder()
                .add(bc1)
                .add(bc2)
                .add(bc3)
                .remove(bc2)
                .remove(bc1);
        var subscriptionService = builder.build();
        assertNotNull(subscriptionService);

        Set<BoundedContext> contexts = builder.contexts();
        assertThat(contexts).containsExactly(bc3);
    }

    @Test
    @MuteLogging
    @DisplayName("allow a service instance serving no types")
    void notInitFromEmptyBuilder() {
        assertDoesNotThrow(
                () -> SubscriptionService.newBuilder()
                        .build()
        );
    }

    /*
     * Subscription tests
     * ------------------
     */

    private TopicFactory topic() {
        return requestFactory.topic();
    }

    private Topic newTopic() {
        return topic().forTarget(Targets.allOf(AggProject.class));
    }

    /**
     * Creates a topic on the type which is not available in the Bounded Context used
     * in these tests.
     */
    private Topic invalidTopic() {
        return topic().allOf(Customer.class);
    }

    /**
     * Creates a subscription with the entity state {@code Customer}, which is not present
     * in the context under the test.
     */
    private Subscription invalidSubscription() {
        var invalidTopic = invalidTopic();
        var id = SubscriptionId.newBuilder()
                .setValue(newUuid())
                .build();
        return Subscription
                .newBuilder()
                .setId(id)
                .setTopic(invalidTopic)
                .build();
    }

    @Nested
    @DisplayName("subscribe to")
    class SubscribeTo {

        @Test
        @DisplayName("entity updates")
        void entityTopic() {
            checkSubscribesTo(AggProject.class);
        }

        @Test
        @DisplayName("events")
        void eventTopic() {
            checkSubscribesTo(AggProjectCreated.class);
        }

        @Test
        @DisplayName("events from abstract reactors")
        @MuteLogging
        void eventsFromReactors() {
            var subscription = checkSubscribesTo(AggOwnerNotified.class);
            MemoizingObserver<SubscriptionUpdate> observer = StreamObservers.memoizingObserver();
            subscriptionService.activate(subscription, observer);
            var projectId = ProjectId.generate();
            var command = new TestActorRequestFactory(SubscriptionServiceTest.class)
                    .createCommand(createProject(projectId));
            context.commandBus()
                   .post(command, noOpObserver());
            var events = observer.firstResponse().getEventUpdates();
            assertThat(events.getEventList())
                    .hasSize(1);
            var event = events.getEvent(0);
            assertThat(event.enclosedMessage())
                    .isInstanceOf(AggOwnerNotified.class);
        }

        @Test
        @DisplayName("events from standalone command handlers")
        @MuteLogging
        void eventsFromCommandHandlers() {
            var subscription = checkSubscribesTo(ReportSent.class);
            MemoizingObserver<SubscriptionUpdate> observer = StreamObservers.memoizingObserver();
            subscriptionService.activate(subscription, observer);
            var command = new TestActorRequestFactory(SubscriptionServiceTest.class)
                    .createCommand(sendReport());
            context.commandBus()
                   .post(command, noOpObserver());
            var events = observer.firstResponse().getEventUpdates();
            assertThat(events.getEventList())
                    .hasSize(1);
            var event = events.getEvent(0);
            assertThat(event.enclosedMessage())
                    .isInstanceOf(ReportSent.class);
        }

        @CanIgnoreReturnValue
        private Subscription checkSubscribesTo(Class<? extends Message> aClass) {
            var target = Targets.allOf(aClass);
            var topic = topic().forTarget(target);
            subscriptionService.subscribe(topic, observer);

            var response = observer.firstResponse();
            var assertResponse = ProtoTruth.assertThat(response);
            assertResponse.isNotNull();
            assertResponse.hasAllRequiredFields();
            assertThat(response.getTopic()
                               .getTarget()
                               .getType())
                    .isEqualTo(target.getType());
            assertThat(observer.getError())
                    .isNull();
            assertThat(observer.isCompleted())
                    .isTrue();
            return response;
        }
    }

    @Nested
    @DisplayName("select proper `BoundedContext` by the type of the requested ")
    class PickContextBy {

        @Test
        @DisplayName("events emitted by one of the bounded context entities")
        void eventsFromEntity() {
            assertTargetFound(AggTaskAdded.class);
        }

        @Test
        @DisplayName("events emitted by a standalone event reactor")
        void eventsFromEventReactor() {
            assertTargetFound(AggOwnerNotified.class);
        }

        @Test
        @DisplayName("events emitted by a standalone command handler")
        void eventsFromCommandHandler() {
            assertTargetFound(ReportSent.class);
        }

        @Test
        @DisplayName("state of entities that belong to a bounded context")
        void entityType() {
            assertTargetFound(AggProject.class);
        }

        private void assertTargetFound(Class<? extends Message> targetType) {
            var target = Targets.allOf(targetType);
            var result = subscriptionService.findContextOf(target);
            assertThat(result).isPresent();
            var actual = result.get();
            assertThat(actual.name())
                    .isEqualTo(context.name());
        }
    }

    @Test
    @DisplayName("activate subscription")
    void activateSubscription() {
        var topic = newTopic();
        // Subscribe to the topic.
        subscriptionService.subscribe(topic, observer);
        verifyState(observer, true);

        // Activate subscription.
        subscriptionService.activate(observer.firstResponse(), activationObserver);

        // Generate the update and get the ID of the updated entity.
        var entityId = updateEntity();

        // `isCompleted` set to false since we don't expect
        // `activationObserver::onCompleted` to be called.
        verifyState(activationObserver, false);

        EntityState<?> actual = memoizedEntity(activationObserver, AggProject.class);
        var expected = toExpected(entityId);
        ProtoTruth.assertThat(actual)
                  .comparingExpectedFieldsOnly()
                  .isEqualTo(expected);
    }

    @CanIgnoreReturnValue
    private ProjectId updateEntity() {
        var projectId = ProjectId.newBuilder()
                .setUuid("some-id")
                .build();
        var cmd = createProject(projectId);
        var command = requestFactory.createCommand(cmd);
        context.commandBus()
               .post(command, noOpObserver());
        return projectId;
    }

    private static EntityState<?> toExpected(ProjectId entityId) {
        return AggProject.newBuilder()
                .setId(entityId)
                .build();
    }

    private static <T extends EntityState<?>>
    T memoizedEntity(MemoizingObserver<SubscriptionUpdate> observer, Class<T> stateType) {
        var update = observer.firstResponse();
        var entityUpdates = update.getEntityUpdates();
        assertThat(entityUpdates.getUpdateCount()).isEqualTo(1);

        var stateUpdate = entityUpdates.getUpdate(0);
        return unpack(stateUpdate.getState(), stateType);
    }

    private static <T> void verifyState(MemoizingObserver<T> observer, boolean completed) {
        assertThat(observer.firstResponse()).isNotNull();
        assertThat(observer.getError()).isNull();
        assertThat(observer.isCompleted()).isEqualTo(completed);
    }

    @Test
    @MuteLogging
    @DisplayName("receive IAE in error callback when activating non-existent subscription")
    void failOnActivatingNonExistent() {
        var invalidSubscription = invalidSubscription();
        subscriptionService.activate(invalidSubscription, activationObserver);
        assertThat(activationObserver.responses())
                .isEmpty();
        assertThat(activationObserver.isCompleted())
                .isFalse();
        assertThat(activationObserver.getError())
                .isInstanceOf(InvalidSubscriptionException.class);
    }

    @Test
    @DisplayName("cancel subscription")
    void cancelSubscription() {
        var topic = newTopic();

        // Subscribe.
        subscriptionService.subscribe(topic, observer);

        // Activate subscription.
        subscriptionService.activate(observer.firstResponse(), activationObserver);

        // Cancel subscription.
        subscriptionService.cancel(observer.firstResponse(), cancellationObserver);

        // Post update to Stand.
        updateEntity();

        // The update must not be handled by the observer.
        assertThat(activationObserver.responses()).isEmpty();
        assertThat(activationObserver.isCompleted()).isFalse();
    }

    @Nested
    @DisplayName("when cancelling non-existent subscription")
    class WarnOnCancelling extends LoggingTest {

        WarnOnCancelling() {
            super(SubscriptionService.class, Level.WARNING);
        }

        @BeforeEach
        void cancelSubscription() {
            var invalidSubscription = invalidSubscription();

            // Hook the log here to minimize the trapped output.
            interceptLogging();

            subscriptionService.cancel(invalidSubscription, cancellationObserver);
        }

        @AfterEach
        void tearDown() {
            restoreLogging();
        }

        @Test
        @DisplayName("do not return the error to the observer")
        void observerIsEmpty() {
            assertThat(cancellationObserver.isCompleted()).isTrue();
            assertThat(cancellationObserver.responses()).isEmpty();
            assertThat(cancellationObserver.getError()).isNull();
        }

        @Test
        @DisplayName("log warning")
        void nonExistingSubscription() {
            assertLog().record()
                       .hasLevelThat()
                       .isEqualTo(level());
        }
    }

    @Nested
    @DisplayName("handle exceptions and call observer error callback for")
    class HandleExceptionsOf {

        @Test
        @MuteLogging
        @DisplayName("subscription process")
        void subscription() {
            // Causes NPE.
            subscriptionService.subscribe(null, observer);
            assertThat(observer.responses()).isEmpty();
            assertThat(observer.isCompleted()).isFalse();
            assertThat(observer.getError()).isInstanceOf(NullPointerException.class);
        }

        @Test
        @MuteLogging
        @DisplayName("activation process")
        void activation() {
            // Causes NPE.
            subscriptionService.activate(null, activationObserver);
            assertThat(activationObserver.responses()).isEmpty();
            assertThat(activationObserver.isCompleted()).isFalse();
            assertThat(activationObserver.getError()).isInstanceOf(NullPointerException.class);
        }

        @Test
        @MuteLogging
        @DisplayName("cancellation process")
        void cancellation() {
            var topic = newTopic();
            subscriptionService.subscribe(topic, observer);

            var rejectionMessage = "Execution breaking exception";
            var faultyObserver = new MemoizingObserver<Response>() {
                @Override
                public void onNext(Response value) {
                    super.onNext(value);
                    throw new RuntimeException(rejectionMessage);
                }
            };
            subscriptionService.cancel(observer.firstResponse(), faultyObserver);
            assertThat(faultyObserver.firstResponse()).isNotNull();
            assertThat(faultyObserver.isCompleted()).isFalse();
            var error = faultyObserver.getError();
            assertThat(error).isInstanceOf(RuntimeException.class);
            assertThat(error.getMessage()).isEqualTo(rejectionMessage);
        }
    }

    @Nested
    @DisplayName("reject calls for subscriptions on internal messages when")
    @MuteLogging
    class HandlingInternal {

        private Topic topicOnInternal() {
            return topic().forTarget(Targets.allOf(BankAccount.class));
        }

        @Test
        @DisplayName("subscribing")
        void subscribing() {
            var topic = topicOnInternal();

            subscriptionService.subscribe(topic, observer);

            assertThat(observer.responses()).isEmpty();
            assertThat(observer.isCompleted()).isFalse();
            assertThat(observer.getError()).isInstanceOf(UnpublishedLanguageException.class);
        }

        @Test
        @DisplayName("activating")
        void activating() {
            var subscription = forgedSubscription();

            subscriptionService.activate(subscription, activationObserver);

            assertThat(activationObserver.responses()).isEmpty();
            assertThat(activationObserver.isCompleted()).isFalse();
            assertThat(activationObserver.getError())
                    .isInstanceOf(UnpublishedLanguageException.class);
        }

        @Test
        @DisplayName("cancelling")
        void cancelling() {
            var subscription = forgedSubscription();

            subscriptionService.cancel(subscription, cancellationObserver);

            assertThat(cancellationObserver.responses()).isEmpty();
            assertThat(cancellationObserver.isCompleted()).isFalse();
            assertThat(cancellationObserver.getError())
                    .isInstanceOf(UnpublishedLanguageException.class);
        }

        /**
         * Creates a subscription on a valid topic and then substitutes a topic
         * on an internal type.
         */
        private Subscription forgedSubscription() {
            var topic = newTopic();
            subscriptionService.subscribe(topic, observer);
            var subscription = observer.firstResponse();

            // Substitute another topic for existing subscription.
            var forgedSubscription = subscription.toBuilder()
                    .setTopic(topicOnInternal())
                    .build();
            return forgedSubscription;
        }
    }
}
