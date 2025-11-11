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

package io.spine.server.stand;

import com.google.protobuf.FieldMask;
import io.spine.base.Identifier;
import io.spine.client.ActorRequestFactory;
import io.spine.client.Query;
import io.spine.client.QueryResponse;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.SubscriptionValidationError;
import io.spine.client.Subscriptions;
import io.spine.client.Target;
import io.spine.client.Targets;
import io.spine.client.Topic;
import io.spine.client.TopicId;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.Given.CustomerAggregate;
import io.spine.server.Given.CustomerAggregateRepository;
import io.spine.server.entity.Repository;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.stand.given.AloneWaiter;
import io.spine.server.stand.given.Given.StandTestProjectionRepository;
import io.spine.server.stand.given.StandTestEnv;
import io.spine.server.stand.given.StandTestEnv.AssertProjectQueryResults;
import io.spine.server.stand.given.StandTestEnv.MemoizeQueryResponseObserver;
import io.spine.server.stand.given.StandTestEnv.MemoizeSubscriptionCallback;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.commandservice.customer.Customer;
import io.spine.test.commandservice.customer.command.CreateCustomer;
import io.spine.test.commandservice.customer.event.CustomerCreated;
import io.spine.test.integration.OrderId;
import io.spine.test.integration.command.PlaceOrder;
import io.spine.test.integration.event.OrderPlaced;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.tenant.TenantAwareTest;
import io.spine.validate.ValidationError;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.EntityQueryToProto.transformWith;
import static io.spine.client.QueryValidationError.INVALID_QUERY;
import static io.spine.client.QueryValidationError.UNSUPPORTED_QUERY_TARGET;
import static io.spine.client.TopicValidationError.INVALID_TOPIC;
import static io.spine.client.TopicValidationError.UNSUPPORTED_TOPIC_TARGET;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.protobuf.Messages.isNotDefault;
import static io.spine.server.entity.given.Given.aggregateOfClass;
import static io.spine.server.entity.given.Given.projectionOfClass;
import static io.spine.server.stand.given.Given.StandTestProjection;
import static io.spine.server.stand.given.StandTestEnv.appendWithGeneratedProjects;
import static io.spine.server.stand.given.StandTestEnv.checkAndGetMessageList;
import static io.spine.server.stand.given.StandTestEnv.checkHasExactlyOne;
import static io.spine.server.stand.given.StandTestEnv.checkTypesEmpty;
import static io.spine.server.stand.given.StandTestEnv.createRequestFactory;
import static io.spine.server.stand.given.StandTestEnv.customerIdFor;
import static io.spine.server.stand.given.StandTestEnv.einCustomer;
import static io.spine.server.stand.given.StandTestEnv.einProject;
import static io.spine.server.stand.given.StandTestEnv.emptyUpdateCallback;
import static io.spine.server.stand.given.StandTestEnv.generateCustomers;
import static io.spine.server.stand.given.StandTestEnv.ids;
import static io.spine.server.stand.given.StandTestEnv.newStand;
import static io.spine.server.stand.given.StandTestEnv.projectIdFor;
import static io.spine.server.stand.given.StandTestEnv.setupExpectedFindAllBehaviour;
import static io.spine.server.stand.given.StandTestEnv.storeSampleProject;
import static io.spine.server.stand.given.StandTestEnv.subscribeAndActivate;
import static io.spine.server.stand.given.StandTestEnv.verifyObserver;
import static io.spine.test.projection.Project.Status.UNDEFINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@DisplayName("`Stand` should")  // It's OK for this test.
class StandTest extends TenantAwareTest {

    private static final int TOTAL_PROJECTS_FOR_BATCH_READING = 10;

    private boolean multitenant = false;

    private ActorRequestFactory requestFactory = createRequestFactory(null);

    protected void setMultitenant(boolean multitenant) {
        this.multitenant = multitenant;
    }

    public ActorRequestFactory getRequestFactory() {
        return requestFactory;
    }

    public void setRequestFactory(ActorRequestFactory requestFactory) {
        this.requestFactory = requestFactory;
    }

    protected boolean isMultitenant() {
        return multitenant;
    }

    @BeforeEach
    protected void setUp() {
        setMultitenant(false);
        requestFactory = createRequestFactory(null);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("projection repositories")
        void projectionRepositories() {
            var multitenant = isMultitenant();
            var boundedContext = BoundedContextBuilder
                    .assumingTests(multitenant)
                    .build();
            var stand = boundedContext.stand();

            checkTypesEmpty(stand);

            var standTestProjectionRepo = new StandTestProjectionRepository();
            stand.registerTypeSupplier(standTestProjectionRepo);
            checkHasExactlyOne(stand.exposedTypes(), Project.getDescriptor());

            var knownAggregateTypes = stand.exposedAggregateTypes();
            // As we registered a projection repo, known aggregate types should be still empty.
            assertTrue(knownAggregateTypes.isEmpty(),
                       "For some reason an aggregate type was registered");

            var anotherTestProjectionRepo = new StandTestProjectionRepository();
            stand.registerTypeSupplier(anotherTestProjectionRepo);
            checkHasExactlyOne(stand.exposedTypes(), Project.getDescriptor());
        }

        @Test
        @DisplayName("aggregate repositories")
        void aggregateRepositories() {
            var boundedContext = BoundedContextBuilder.assumingTests().build();
            var stand = boundedContext.stand();

            checkTypesEmpty(stand);

            var customerAggregateRepo = new CustomerAggregateRepository();
            stand.registerTypeSupplier(customerAggregateRepo);

            var customerEntityDescriptor = Customer.getDescriptor();
            checkHasExactlyOne(stand.exposedTypes(), customerEntityDescriptor);
            checkHasExactlyOne(stand.exposedAggregateTypes(), customerEntityDescriptor);

            @SuppressWarnings("LocalVariableNamingConvention")
            var anotherCustomerAggregateRepo =
                    new CustomerAggregateRepository();
            stand.registerTypeSupplier(anotherCustomerAggregateRepo);
            checkHasExactlyOne(stand.exposedTypes(), customerEntityDescriptor);
            checkHasExactlyOne(stand.exposedAggregateTypes(), customerEntityDescriptor);
        }
    }

    @Nested
    @DisplayName("return empty list")
    class ReturnEmptyList {

        @Test
        @DisplayName("on read all when empty")
        void onReadAllWhenEmpty() {
            var readAllCustomers = requestFactory.query().all(Customer.class);
            checkEmptyResultForTargetOnEmptyStorage(readAllCustomers);
        }

        @Test
        @DisplayName("on read by IDs when empty")
        void onReadByIdWhenEmpty() {

            var readCustomersById =
                    requestFactory.query()
                                  .byIds(Customer.class,
                                         newHashSet(customerIdFor(1), customerIdFor(2)));

            checkEmptyResultForTargetOnEmptyStorage(readCustomersById);
        }

        private void checkEmptyResultForTargetOnEmptyStorage(Query readCustomersQuery) {
            var stand = createStand();

            var responseObserver = new MemoizeQueryResponseObserver();
            stand.execute(readCustomersQuery, responseObserver);

            var messageList = checkAndGetMessageList(responseObserver);
            assertTrue(
                    messageList.isEmpty(),
                    "Query returned a non-empty response message list though the target had " +
                            "been empty"
            );
        }
    }

    @Nested
    @DisplayName("return single result")
    class ReturnSingleResult {

        @Test
        @DisplayName("for projection read by ID")
        void forProjectionReadById() {
            doCheckReadingProjectsById(1);
        }
    }

    @Nested
    @DisplayName("return multiple results")
    class ReturnMultipleResults {

        @Test
        @DisplayName("for projection batch read by IDs")
        void forProjectionBatchRead() {
            doCheckReadingProjectsById(TOTAL_PROJECTS_FOR_BATCH_READING);
        }

        @Test
        @DisplayName("for projection batch read by IDs with field mask")
        void forProjectionReadWithMask() {
            checkReadingByIdAndMask(Project.Field.id()
                                                 .getField(),
                                    Project.Field.name()
                                                 .getField());
        }

        private void checkReadingByIdAndMask(io.spine.base.Field... maskingFields) {
            var repository = new StandTestProjectionRepository();
            var stand = createStand(repository);

            var querySize = 2;
            var projectVersion = 1;

            Set<ProjectId> ids = new HashSet<>();
            for (var i = 0; i < querySize; i++) {
                var id = projectIdFor(i);
                storeSampleProject(repository, id, String.valueOf(i), projectVersion);
                ids.add(id);
            }

            var queryFactory = requestFactory.query();
            var builder =
                    Project.query()
                           .id().in(ids)
                           .withMask(maskingFields);
            @SuppressWarnings("OptionalGetWithoutIsPresent")    // The value just set above.
            var fieldMask = builder.whichMask()
                                   .get();
            var query = builder.build(transformWith(queryFactory));

            MemoizeQueryResponseObserver observer =
                    new AssertProjectQueryResults(ids, projectVersion, fieldMask);

            stand.execute(query, observer);

            verifyObserver(observer);
        }
    }

    @Nested
    @DisplayName("trigger subscription callback")
    class TriggerSubscriptionCallback {

        @Test
        @DisplayName("upon update of aggregate")
        void uponUpdateOfAggregate() {
            // Subscribe to changes of Customer aggregate.
            var repository = new CustomerAggregateRepository();
            var stand = createStand(repository);
            var allCustomers = requestFactory.topic().allOf(Customer.class);

            var callback = new MemoizeSubscriptionCallback();
            subscribeAndActivate(stand, allCustomers, callback);
            assertNull(callback.newEntityState());

            // Post a new entity state.
            var customer = einCustomer();
            var customerId = customer.getId();
            var version = 1;
            var entity = aggregateOfClass(CustomerAggregate.class)
                    .withId(customerId)
                    .withState(customer)
                    .withVersion(version)
                    .build();

            stand.post(entity, repository.lifecycleOf(customerId));

            // Check the callback is called with the correct value.
            var packedState = AnyPacker.pack(customer);
            assertEquals(packedState, callback.newEntityState());
        }

        @Test
        @DisplayName("upon update of projection")
        void uponUpdateOfProjection() {
            // Subscribe to changes of StandTest projection.
            var repository = new StandTestProjectionRepository();
            var stand = createStand(repository);
            var allProjects = requestFactory.topic().allOf(Project.class);

            var callback = new MemoizeSubscriptionCallback();
            subscribeAndActivate(stand, allProjects, callback);
            assertNull(callback.newEntityState());

            // Post a new entity state.
            var project = einProject();
            var projectId = project.getId();
            var version = 1;
            var entity = projectionOfClass(StandTestProjection.class)
                    .withId(projectId)
                    .withState(project)
                    .withVersion(version)
                    .build();
            stand.post(entity, repository.lifecycleOf(projectId));

            // Check the callback is called with the correct value.
            var packedState = AnyPacker.pack(project);
            assertThat(packedState).isEqualTo(callback.newEntityState());
        }

        @Test
        @DisplayName("upon receiving the event of observed type, when event is produced by Entity")
        void uponEventFromEntity() {
            // Subscribe to Customer aggregate updates.
            var repository = new CustomerAggregateRepository();
            var stand = createStand(repository);
            var topic = requestFactory.topic().allOf(CustomerCreated.class);
            var callback = new MemoizeSubscriptionCallback();
            subscribeAndActivate(stand, topic, callback);

            // Send a command creating a new Customer and triggering a CustomerCreated event.
            var customer = einCustomer();
            var customerId = customer.getId();
            var createCustomer = CreateCustomer.newBuilder()
                    .setCustomerId(customerId)
                    .setCustomer(customer)
                    .build();
            var command = requestFactory.command().create(createCustomer);
            var cmd = CommandEnvelope.of(command);
            repository.dispatch(cmd);

            // Check the callback is called with the correct event.
            var event = callback.newEvent();
            assertNotNull(event);

            var context = event.context();
            var origin = context.getPastMessage().messageId();
            assertThat(origin.asCommandId()).isEqualTo(cmd.id());
            assertThat(origin.getTypeUrl()).isEqualTo(cmd.command()
                                                         .enclosedTypeUrl()
                                                         .value());
            var packedMessage = event.getMessage();
            var eventMessage = unpack(packedMessage, CustomerCreated.class);

            assertThat(eventMessage.getCustomerId())
                    .isEqualTo(customerId);
            assertThat(eventMessage.getCustomer())
                    .isEqualTo(customer);
        }

        @Test
        @DisplayName("upon receiving the event of observed type, " +
                "when event is produced by a standalone command assignee")
        void uponEventFromStandaloneAssignee() {
            var waiter = new AloneWaiter();
            var context =
                    BoundedContextBuilder.assumingTests(isMultitenant())
                                         .addCommandDispatcher(waiter)
                                         .build();
            var stand = context.stand();
            var topic = requestFactory.topic().allOf(OrderPlaced.class);

            var callback = new MemoizeSubscriptionCallback();
            subscribeAndActivate(stand, topic, callback);

            var orderId = OrderId.generate();
            var placeOrder = PlaceOrder.newBuilder()
                    .setId(orderId)
                    .build();
            var command = requestFactory.command().create(placeOrder);

            assertThat(waiter.ordersPlaced()).isEqualTo(0);
            context.commandBus().post(command, noOpObserver());
            assertThat(waiter.ordersPlaced()).isEqualTo(1);

            @Nullable Event observed = callback.newEvent();
            assertThat(observed).isNotNull();
            var expected = OrderPlaced.newBuilder()
                    .setId(orderId)
                    .build();
            assertThat(observed.enclosedMessage()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("trigger subscription callbacks matching by ID")
    void triggerSubscriptionsMatchingById() {
        var repository = new CustomerAggregateRepository();
        var stand = createStand(repository);

        var sampleCustomers = generateCustomers(10);

        var someCustomers = requestFactory.topic()
                                          .select(Customer.class)
                                          .byId(ids(sampleCustomers))
                                          .build();
        Collection<Customer> callbackStates = newHashSet();
        var callback = new MemoizeSubscriptionCallback() {
            @Override
            public void accept(SubscriptionUpdate update) {
                super.accept(update);
                var customerInCallback = (Customer) update.state(0);
                callbackStates.add(customerInCallback);
            }
        };
        subscribeAndActivate(stand, someCustomers, callback);

        for (var customer : sampleCustomers) {
            var customerId = customer.getId();
            var version = 1;
            var entity = aggregateOfClass(CustomerAggregate.class)
                    .withId(customerId)
                    .withState(customer)
                    .withVersion(version)
                    .build();
            stand.post(entity, repository.lifecycleOf(customerId));
        }

        assertEquals(newHashSet(sampleCustomers), callbackStates);
    }

    @Test
    @DisplayName("allow cancelling subscriptions")
    void cancelSubscriptions() {
        var repository = new CustomerAggregateRepository();
        var stand = createStand(repository);
        var allCustomers = requestFactory.topic().allOf(Customer.class);

        var callback = new MemoizeSubscriptionCallback();
        var subscription = subscribeAndActivate(stand, allCustomers, callback);

        stand.cancel(subscription, noOpObserver());

        assertNoUpdates(stand, repository, callback);
    }

    @Test
    @DisplayName("allow cancelling a subscription " +
            "even if its internal properties were modified, but ID is the same")
    void cancelModifiedSubscriptions() {
        var repository = new CustomerAggregateRepository();
        var stand = createStand(repository);
        var allCustomers = requestFactory.topic().allOf(Customer.class);
        var callback = new MemoizeSubscriptionCallback();
        var subscription = subscribeAndActivate(stand, allCustomers, callback);

        var modifiedSubscription = StandTestEnv.resetTimestamp(subscription);
        stand.cancel(modifiedSubscription, noOpObserver());

        assertNoUpdates(stand, repository, callback);
    }

    private static void
    assertNoUpdates(Stand stand,
                    CustomerAggregateRepository repository,
                    MemoizeSubscriptionCallback callback) {
        var customer = generateCustomers(1).iterator()
                                           .next();
        var customerId = customer.getId();
        var version = 1;
        var entity = aggregateOfClass(CustomerAggregate.class)
                .withId(customerId)
                .withState(customer)
                .withVersion(version)
                .build();
        stand.post(entity, repository.lifecycleOf(customerId));

        assertNull(callback.newEntityState());
    }

    @Test
    @DisplayName("fail if cancelling non-existent subscription")
    void notCancelNonExistent() {
        var stand = createStand();
        var topicId = TopicId.newBuilder()
                .setValue(Identifier.newUuid())
                .build();
        var nonExistingSubscription = Subscription.newBuilder()
                .setId(Subscriptions.generateId())
                .setTopic(Topic.newBuilder().setId(topicId).buildPartial())
                .buildPartial();
        assertThrows(InvalidSubscriptionException.class,
                     () -> stand.cancel(nonExistingSubscription, noOpObserver()));
    }

    @SuppressWarnings("MethodWithMultipleLoops")
    @Test
    @DisplayName("trigger each subscription callback once for multiple subscriptions")
    void triggerSubscriptionCallbackOnce() {
        var repository = new CustomerAggregateRepository();
        var stand = createStand(repository);
        var allCustomers = Targets.allOf(Customer.class);

        Set<MemoizeSubscriptionCallback> callbacks = newHashSet();
        var totalCallbacks = 100;

        for (var callbackIndex = 0; callbackIndex < totalCallbacks; callbackIndex++) {
            var callback = subscribeWithCallback(stand, allCustomers);
            callbacks.add(callback);
        }

        var customer = generateCustomers(1)
                .iterator()
                .next();
        var customerId = customer.getId();
        var version = 1;
        var entity = aggregateOfClass(CustomerAggregate.class)
                .withId(customerId)
                .withState(customer)
                .withVersion(version)
                .build();
        stand.post(entity, repository.lifecycleOf(customerId));

        var packedState = AnyPacker.pack(customer);
        for (var callback : callbacks) {
            assertEquals(packedState, callback.newEntityState());
            assertEquals(1, callback.countAcceptedUpdates());
        }
    }

    @Test
    @DisplayName("not trigger subscription callbacks in case of another type criterion mismatch")
    void notTriggerOnTypeMismatch() {
        var repository = new CustomerAggregateRepository();
        var projectionRepository = new StandTestProjectionRepository();
        var stand = createStand(repository, projectionRepository);
        var allProjects = Targets.allOf(Project.class);
        var callback = subscribeWithCallback(stand, allProjects);
        var customer = einCustomer();
        var customerId = customer.getId();
        var version = 1;
        var entity = aggregateOfClass(CustomerAggregate.class)
                .withId(customerId)
                .withState(customer)
                .withVersion(version)
                .build();
        stand.post(entity, repository.lifecycleOf(customerId));

        assertEquals(0, callback.countAcceptedUpdates());
    }

    private MemoizeSubscriptionCallback subscribeWithCallback(Stand stand, Target target) {
        var callback = new MemoizeSubscriptionCallback();
        var topic = requestFactory.topic()
                                  .forTarget(target);
        subscribeAndActivate(stand, topic, callback);

        assertNull(callback.newEntityState());
        return callback;
    }

    @Test
    @DisplayName("query `AggregateRepository` for aggregate states")
    void readAggregates() {
        var multitenant = isMultitenant();
        var stand = Stand.newBuilder()
                .setMultitenant(multitenant)
                .build();
        var repository = new CustomerAggregateRepository();
        BoundedContextBuilder.assumingTests()
                             .add(repository)
                             .build();
        stand.registerTypeSupplier(repository);
        var queryFactory = getRequestFactory().query();
        var query =
                Customer.query()
                        .firstName().is("George")
                        .build(transformWith(queryFactory));
        stand.execute(query, noOpObserver());

        var actualFilter = repository.memoizedFilters();
        assertThat(actualFilter).isPresent();
        assertThat(actualFilter)
              .hasValue(query.filters());

        var actualFormat = repository.memoizedFormat();
        assertThat(actualFormat).isPresent();
        assertThat(actualFormat)
              .hasValue(query.getFormat());
    }

    @Test
    @MuteLogging
    @DisplayName("handle mistakes in query silently")
    void handleMistakesInQuery() {
        var repository = new StandTestProjectionRepository();
        var stand = createStand(repository);
        var projectVersion = storeSampleProject(repository);

        var thirdField = Project.getDescriptor()
                                .getFields()
                                .get(2)
                                .getFullName();
        var queryFactory = requestFactory.query();

        // FieldMask with invalid field paths.
        var mask = FieldMask.newBuilder()
                .addPaths("invalid_field_path_example")
                .addPaths(thirdField)
                .build();
        var query = Project.query()
                           .withMask(mask)
                           .build(transformWith(queryFactory));
        var observer = new MemoizeQueryResponseObserver() {
            @Override
            public void onNext(QueryResponse response) {
                super.onNext(response);
                assertFalse(response.isEmpty());

                var project = (Project) response.state(0);

                assertNotNull(project);
                assertFalse(project.hasId());
                assertThat(project.getName()).isEmpty();
                assertEquals(UNDEFINED, project.getStatus());
                assertThat(project.getTaskList()).isEmpty();

                var version = response.version(0);
                assertThat(version.getNumber()).isEqualTo(projectVersion);
            }
        };
        stand.execute(query, observer);
        verifyObserver(observer);
    }

    @Nested
    @DisplayName("throw invalid query exception packed as `IAE`")
    class ThrowInvalidQueryEx {

        @Test
        @DisplayName("if querying unknown type")
        void ifQueryingUnknownType() {
            var stand = Stand.newBuilder()
                    .setMultitenant(isMultitenant())
                    .build();
            checkTypesEmpty(stand);

            // Customer type was NOT registered.
            // So create a query for an unknown type.
            var readAllCustomers = requestFactory.query()
                                                 .all(Customer.class);
            var exception =
                    assertThrows(InvalidQueryException.class,
                                 () -> stand.execute(readAllCustomers, noOpObserver()));
            assertEquals(readAllCustomers, exception.getRequest());

            assertEquals(UNSUPPORTED_QUERY_TARGET.getNumber(),
                         exception.asError()
                                  .getCode());
        }

        @Test
        @DisplayName("if invalid query message is passed")
        void ifInvalidQueryMessagePassed() {
            var stand = createStand();
            var invalidQuery = Query.getDefaultInstance();

            var exception =
                    assertThrows(InvalidQueryException.class,
                                 () -> stand.execute(invalidQuery, noOpObserver()));
            assertEquals(invalidQuery, exception.getRequest());

            assertEquals(INVALID_QUERY.getNumber(),
                         exception.asError()
                                  .getCode());
            var validationError = AnyPacker.unpack(exception.asError().getDetails());
            assertThat(validationError).isInstanceOf(ValidationError.class);
            assertTrue(isNotDefault(validationError));
        }
    }

    @Nested
    @DisplayName("throw invalid topic exception packed as `IAE`")
    class ThrowInvalidTopicEx {

        @Test
        @DisplayName("if subscribing to unknown type changes")
        void ifSubscribingToUnknownType() {
            var stand = Stand.newBuilder()
                    .setMultitenant(isMultitenant())
                    .build();
            checkTypesEmpty(stand);

            // Project type was NOT registered.
            // So create a topic for an unknown type.
            var allProjectsTopic = requestFactory.topic().allOf(Project.class);
            var exception =
                    assertThrows(InvalidTopicException.class,
                                 () -> stand.subscribe(allProjectsTopic, noOpObserver()));
            assertEquals(allProjectsTopic, exception.getRequest());

            assertEquals(UNSUPPORTED_TOPIC_TARGET.getNumber(),
                         exception.asError()
                                  .getCode());
        }

        @Test
        @DisplayName("if invalid topic message is passed")
        void ifInvalidTopicMessagePassed() {
            var stand = createStand();
            var invalidTopic = Topic.getDefaultInstance();
            var exception =
                    assertThrows(InvalidTopicException.class,
                                 () -> stand.subscribe(invalidTopic, noOpObserver()));
            assertEquals(invalidTopic, exception.getRequest());

            assertEquals(INVALID_TOPIC.getNumber(),
                         exception.asError()
                                  .getCode());

            var validationError = AnyPacker.unpack(exception.asError().getDetails());
            assertThat(validationError).isInstanceOf(ValidationError.class);
            assertTrue(isNotDefault(validationError));
        }
    }

    @Nested
    @DisplayName("throw invalid subscription exception packed as `IAE`")
    class ThrowInvalidSubscriptionEx {

        @Test
        @DisplayName("if activating subscription with unsupported target")
        void ifActivateWithUnsupportedTarget() {
            var stand = createStand();
            var subscription = subscriptionWithUnknownTopic();

            var exception =
                    assertThrows(InvalidSubscriptionException.class,
                                 () -> stand.activate(subscription,
                                                      emptyUpdateCallback(),
                                                      noOpObserver()));
            verifyUnknownSubscription(subscription, exception);
        }

        @Test
        @DisplayName("if cancelling subscription with unsupported target")
        void ifCancelWithUnsupportedTarget() {
            var stand = createStand();
            var subscription = subscriptionWithUnknownTopic();

            var exception =
                    assertThrows(InvalidSubscriptionException.class,
                                 () -> stand.cancel(subscription, noOpObserver()));
            verifyUnknownSubscription(subscription, exception);
        }

        @Test
        @DisplayName("if invalid subscription message is passed on activation")
        void ifActivateWithInvalidMessage() {
            var stand = createStand();
            var invalidSubscription = Subscription.getDefaultInstance();

            var exception =
                    assertThrows(InvalidSubscriptionException.class,
                                 () -> stand.activate(invalidSubscription,
                                                      emptyUpdateCallback(),
                                                      noOpObserver()));
            verifyInvalidSubscription(invalidSubscription, exception);

        }

        @Test
        @DisplayName("if invalid subscription message is passed on cancel")
        void ifCancelWithInvalidMessage() {
            var stand = createStand();
            var invalidSubscription = Subscription.getDefaultInstance();

            var exception =
                    assertThrows(InvalidSubscriptionException.class,
                                 () -> stand.cancel(invalidSubscription, noOpObserver()));
            verifyInvalidSubscription(invalidSubscription, exception);
        }

        private static void verifyInvalidSubscription(Subscription invalidSubscription,
                                                      InvalidSubscriptionException exception) {
            assertEquals(invalidSubscription, exception.getRequest());

            assertEquals(SubscriptionValidationError.INVALID_SUBSCRIPTION.getNumber(),
                         exception.asError()
                                  .getCode());

            var validationError = (ValidationError)
                    AnyPacker.unpack(exception.asError().getDetails());
            assertTrue(isNotDefault(validationError));
        }

        private static void verifyUnknownSubscription(Subscription subscription,
                                                      InvalidSubscriptionException exception) {
            assertEquals(subscription, exception.getRequest());

            assertEquals(SubscriptionValidationError.UNKNOWN_SUBSCRIPTION.getNumber(),
                         exception.asError()
                                  .getCode());
        }

        private Subscription subscriptionWithUnknownTopic() {
            var allCustomersTopic = requestFactory.topic().allOf(Customer.class);
            return Subscription.newBuilder()
                    .setId(Subscriptions.generateId())
                    .setTopic(allCustomersTopic)
                    .build();
        }
    }

    private Stand createStand() {
        return newStand(isMultitenant());
    }

    @SuppressWarnings("OverloadedVarargsMethod") // OK for this helper method.
    private Stand createStand(Repository<?, ?>... repositories) {
        return newStand(isMultitenant(), repositories);
    }

    private void doCheckReadingProjectsById(int numberOfProjects) {
        // Define the types and values used as a test data.
        Map<ProjectId, Project> sampleProjects = new HashMap<>();
        appendWithGeneratedProjects(sampleProjects, numberOfProjects);

        var projectionRepository = new StandTestProjectionRepository();
        setupExpectedFindAllBehaviour(projectionRepository, sampleProjects);

        var stand = standWithRepo(projectionRepository);

        var queryFactory = requestFactory.query();
        var readMultipleProjects =
                Project.query()
                       .id().in(sampleProjects.keySet())
                       .build(transformWith(queryFactory));

        var responseObserver = new MemoizeQueryResponseObserver();
        stand.execute(readMultipleProjects, responseObserver);

        var messageList = checkAndGetMessageList(responseObserver);
        assertEquals(sampleProjects.size(), messageList.size());
        var allCustomers = sampleProjects.values();
        for (var singleRecord : messageList) {
            var state = singleRecord.getState();
            var unpackedSingleResult = unpack(state, Project.class);
            assertTrue(allCustomers.contains(unpackedSingleResult));
        }
    }

    private Stand standWithRepo(ProjectionRepository<?, ?, ?> projectionRepository) {
        var stand = createStand();
        assertNotNull(stand);
        stand.registerTypeSupplier(projectionRepository);
        return stand;
    }
}
