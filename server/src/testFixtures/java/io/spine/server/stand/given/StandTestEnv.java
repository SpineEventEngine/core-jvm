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

package io.spine.server.stand.given;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.grpc.stub.StreamObserver;
import io.spine.base.Identifier;
import io.spine.client.ActorRequestFactory;
import io.spine.client.EntityStateWithVersion;
import io.spine.client.Query;
import io.spine.client.QueryResponse;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Topic;
import io.spine.core.Event;
import io.spine.core.Responses;
import io.spine.core.TenantId;
import io.spine.grpc.MemoizingObserver;
import io.spine.people.PersonName;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.Given.CustomerAggregateRepository;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.Repository;
import io.spine.server.stand.Stand;
import io.spine.server.stand.SubscriptionCallback;
import io.spine.server.stand.given.Given.StandTestProjectionRepository;
import io.spine.test.commandservice.customer.Customer;
import io.spine.test.commandservice.customer.CustomerId;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import io.spine.testing.core.given.GivenUserId;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.entity.given.Given.projectionOfClass;
import static io.spine.test.projection.Project.Status.STARTED;
import static io.spine.testing.Assertions.assertMatchesMask;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class StandTestEnv {

    private static final ImmutableList<String> FIRST_NAMES = ImmutableList.of(
            "Emma", "Liam", "Mary", "John"
    );
    private static final ImmutableList<String> LAST_NAMES = ImmutableList.of(
            "Smith", "Doe", "Steward", "Lee"
    );

    /** Prevents this utility class from instantiation. */
    private StandTestEnv() {
    }

    public static Stand newStand(boolean multitenant) {
        return newStand(multitenant,
                        new CustomerAggregateRepository(), new StandTestProjectionRepository());
    }

    public static Stand newStand(boolean multitenant, Repository<?, ?>... repositories) {
        var builder = BoundedContextBuilder.assumingTests(multitenant);
        for (var repository : repositories) {
            builder.add(repository);
        }
        var context = builder.build();
        return context.stand();
    }

    public static Stand newStand(CommandDispatcher... dispatchers) {
        var builder = BoundedContextBuilder.assumingTests();
        for (var dispatcher : dispatchers) {
            builder.addCommandDispatcher(dispatcher);
        }
        var context = builder.build();
        return context.stand();
    }

    public static ActorRequestFactory createRequestFactory(@Nullable TenantId tenant) {
        var builder = ActorRequestFactory.newBuilder()
                .setActor(GivenUserId.of(newUuid()));
        if (tenant != null) {
            builder.setTenantId(tenant);
        }
        return builder.build();
    }

    public static CustomerId customerIdFor(int numericId) {
        return CustomerId.newBuilder()
                         .setNumber(numericId)
                         .build();
    }

    public static ProjectId projectIdFor(int numericId) {
        return ProjectId.newBuilder()
                        .setId(String.valueOf(numericId))
                        .build();
    }

    public static int storeSampleProject(StandTestProjectionRepository repository) {
        var id = projectIdFor(42);
        var projectVersion = 42;
        storeSampleProject(repository, id, "Some sample project", projectVersion);
        return projectVersion;
    }

    public static void storeSampleProject(StandTestProjectionRepository repository,
                                          ProjectId id,
                                          String name,
                                          int projectVersion) {
        var project = Project.newBuilder()
                .setId(id)
                .setName(name)
                .setStatus(STARTED)
                .build();
        repository.store(projectionOfClass(Given.StandTestProjection.class)
                                 .withId(project.getId())
                                 .withState(project)
                                 .withVersion(projectVersion)
                                 .build());
    }

    @CanIgnoreReturnValue
    public static Subscription subscribeAndActivate(Stand stand, Topic topic,
                                                    SubscriptionCallback callback) {
        MemoizingObserver<Subscription> observer = memoizingObserver();
        stand.subscribe(topic, observer);
        var subscription = observer.firstResponse();
        stand.activate(subscription, callback, noOpObserver());

        assertNotNull(subscription);
        return subscription;
    }

    public static void verifyObserver(MemoizeQueryResponseObserver observer) {
        assertNotNull(observer.responseHandled());
        assertTrue(observer.isCompleted());
        assertNull(observer.throwable());
    }

    public static void setupExpectedFindAllBehaviour(StandTestProjectionRepository repo,
                                                     Map<ProjectId, Project> contentToReturn) {
        var projectIds = contentToReturn.keySet();
        var allRecords = toProjectionRecords(projectIds);

        repo.setRecords(allRecords.iterator());
    }

    public static Collection<Customer> generateCustomers(int howMany) {
        return generate(howMany,
                        numericId -> Customer.newBuilder()
                                             .setId(customerIdFor(numericId))
                                             .setName(personName())
                                             .build());
    }

    public static Customer einCustomer() {
        return generateCustomers(1).iterator()
                                   .next();
    }

    public static void appendWithGeneratedProjects(Map<ProjectId, Project> destination,
                                                   int howMany) {
        for (var projectIndex = 0; projectIndex < howMany; projectIndex++) {
            var project = Project.getDefaultInstance();
            var projectId = ProjectId.newBuilder()
                    .setId(UUID.randomUUID()
                               .toString())
                    .build();
            destination.put(projectId, project);
        }
    }

    public static Project einProject() {
        return generateProjects(1).iterator()
                                  .next();
    }

    private static Collection<Project> generateProjects(int howMany) {
        return generate(howMany,
                        numericId -> Project.newBuilder()
                                            .setId(projectIdFor(numericId))
                                            .setName(String.valueOf(numericId))
                                            .build());
    }

    private static PersonName personName() {
        var givenName = selectOne(FIRST_NAMES);
        var familyName = selectOne(LAST_NAMES);
        return PersonName.newBuilder()
                .setGivenName(givenName)
                .setFamilyName(familyName)
                .build();
    }

    private static <T> T selectOne(List<T> choices) {
        checkArgument(!choices.isEmpty());
        Random random = new SecureRandom();
        var index = random.nextInt(choices.size());
        return choices.get(index);
    }

    private static ImmutableCollection<EntityRecord>
    toProjectionRecords(Collection<ProjectId> projectionIds) {
        var transformed = Collections2.transform(
                projectionIds,
                input -> {
                    checkNotNull(input);
                    var projection = new Given.StandTestProjection(input);
                    var id = Identifier.pack(projection.id());
                    var state = AnyPacker.pack(projection.state());
                    var record = EntityRecord.newBuilder()
                            .setEntityId(id)
                            .setState(state)
                            .build();
                    return record;
                });
        var result = ImmutableList.copyOf(transformed);
        return result;
    }

    private static <T extends Message> Collection<T> generate(int count, IntFunction<T> idMapper) {
        Random random = new SecureRandom();
        var result = IntStream.generate(random::nextInt)
                              .limit(count)
                              .map(Math::abs)
                              .mapToObj(idMapper)
                              .collect(toList());
        return result;
    }

    public static List<EntityStateWithVersion>
    checkAndGetMessageList(MemoizeQueryResponseObserver responseObserver) {
        assertTrue(responseObserver.isCompleted(), "Query has not completed successfully");
        assertNull(responseObserver.throwable(), "Throwable has been caught upon query execution");

        var response = responseObserver.responseHandled();
        assertEquals(Responses.ok(), response.getResponse(), "Query response is not OK");
        assertNotNull(response, "Query response must not be null");

        var messages = response.getMessageList();
        assertNotNull(messages, "Query response has null message list");
        return messages;
    }

    public static void checkTypesEmpty(Stand stand) {
        assertTrue(stand.exposedTypes()
                        .isEmpty());
        assertTrue(stand.exposedAggregateTypes()
                        .isEmpty());
    }

    public static void checkHasExactlyOne(Collection<TypeUrl> availableTypes,
                                          Descriptors.Descriptor expectedType) {
        assertEquals(1, availableTypes.size());

        var actualTypeUrl = availableTypes.iterator().next();
        var expectedTypeUrl = TypeUrl.from(expectedType);
        assertEquals(expectedTypeUrl, actualTypeUrl, "Type was registered incorrectly");
    }

    @SuppressWarnings("UnnecessaryLambda") // To give a name to an empty callback.
    public static SubscriptionCallback emptyUpdateCallback() {
        return newEntityState -> {
            //do nothing
        };
    }

    public static Set<CustomerId> ids(Collection<Customer> customers) {
        return customers.stream()
                        .map(Customer::getId)
                        .collect(toSet());
    }

    public static Subscription resetTimestamp(Subscription subscription) {
        var subscriptionBuilder = subscription.toBuilder();
        var topicBuilder = subscriptionBuilder.getTopic().toBuilder();
        var context = topicBuilder.getContext();
        var originalTimestamp = context.getTimestamp();

        var someDuration = Duration.newBuilder()
                .setSeconds(42L)
                .build();
        var newValue = Timestamps.add(Timestamp.getDefaultInstance(), someDuration);

        assertThat(originalTimestamp)
                .isNotEqualTo(newValue);

        var modifiedContext = context.toBuilder()
                .setTimestamp(newValue)
                .build();
        var modifiedTopic = topicBuilder
                .setContext(modifiedContext)
                .build();
        var modifiedSubscription = subscriptionBuilder.setTopic(modifiedTopic)
                                                      .build();
        return modifiedSubscription;
    }

    /**
     * A {@link StreamObserver} storing the state of {@link Query} execution.
     */
    public static class MemoizeQueryResponseObserver implements StreamObserver<QueryResponse> {

        private QueryResponse responseHandled;
        private Throwable throwable;
        private boolean isCompleted = false;

        @Override
        public void onNext(QueryResponse response) {
            this.responseHandled = response;
        }

        @Override
        public void onError(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public void onCompleted() {
            this.isCompleted = true;
        }

        public QueryResponse responseHandled() {
            return responseHandled;
        }

        public Throwable throwable() {
            return throwable;
        }

        public boolean isCompleted() {
            return isCompleted;
        }
    }

    /**
     * A subscription callback, which remembers the updates fed to it.
     */
    public static class MemoizeSubscriptionCallback implements SubscriptionCallback {

        private final List<SubscriptionUpdate> acceptedUpdates = new ArrayList<>();
        private @Nullable Any newEntityState = null;
        private @Nullable Event newEvent = null;

        /**
         * {@inheritDoc}
         *
         * <p>Currently there is always exactly one {@code EntityStateUpdate} in a
         * {@code SubscriptionUpdate}.
         */
        @Override
        public void accept(SubscriptionUpdate update) {
            acceptedUpdates.add(update);
            switch (update.getUpdateCase()) {
                case ENTITY_UPDATES:
                    var entityStateUpdate = update.getEntityUpdates()
                                                  .getUpdateList()
                                                  .get(0);
                    newEntityState = entityStateUpdate.getState();
                    break;
                case EVENT_UPDATES:
                    newEvent = update.getEventUpdates()
                                     .getEventList()
                                     .get(0);
                    break;
                default:
                    // Do nothing.
            }
        }

        public @Nullable Any newEntityState() {
            return newEntityState;
        }

        public @Nullable Event newEvent() {
            return newEvent;
        }

        public int countAcceptedUpdates() {
            return acceptedUpdates.size();
        }
    }

    /**
     * Observes the results of the query sent to read the {@link Project} entity states via Stand.
     */
    public static final class AssertProjectQueryResults extends MemoizeQueryResponseObserver {

        private final ImmutableSet<ProjectId> ids;
        private final int projectVersion;
        private final FieldMask fieldMask;

        /**
         * Creates a new version of this observer taking the expected values as parameters.
         *
         * @param ids
         *         the IDs of the projects which should be returned in the query results
         * @param version
         *         the version each of the {@code Project}s in the query results
         * @param mask
         *         the field mask to which each of the returned entity states is expected to conform
         */
        public AssertProjectQueryResults(Set<ProjectId> ids, int version, FieldMask mask) {
            this.ids = ImmutableSet.copyOf(ids);
            projectVersion = version;
            fieldMask = mask;
        }

        @Override
        public void onNext(QueryResponse value) {
            super.onNext(value);
            var messages = value.getMessageList();
            assertThat(messages).hasSize(ids.size());
            for (var stateWithVersion : messages) {
                var state = stateWithVersion.getState();
                var project = unpack(state, Project.class);
                assertThat(project).isNotNull();
                assertMatchesMask(project, fieldMask);

                var version = stateWithVersion.getVersion();
                assertThat(version.getNumber())
                        .isEqualTo(projectVersion);
            }
        }
    }
}
