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

package io.spine.server.aggregate.given.repo;

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.test.aggregate.ProjectId;
import io.spine.testdata.Sample;
import io.spine.testing.client.TestActorRequestFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;

public class AggregateRepositoryTestEnv {

    private static final TestActorRequestFactory requestFactory = newRequestFactory();
    private static @MonotonicNonNull BoundedContext context;
    private static ProjectAggregateRepository repository = newRepository();

    /** Prevent instantiation of this utility class. */
    private AggregateRepositoryTestEnv() {
    }

    public static TestActorRequestFactory requestFactory() {
        return requestFactory;
    }

    /**
     * Obtains the test {@link #context}.
     *
     * @throws NullPointerException
     *         if the context is not {@linkplain #resetBoundedContext(BoundedContextBuilder)
     *         built} yet
     */
    public static BoundedContext context() {
        return checkNotNull(context, "The test context is not built yet.");
    }

    public static ProjectAggregateRepository repository() {
        return repository;
    }

    /**
     * Builds a new {@link BoundedContext} by the given builder, with the test
     * {@link #repository} added, and assigns it to the test {@link #context}.
     */
    public static void resetBoundedContext(BoundedContextBuilder builder) {
        context = builder.add(repository)
                .build();
    }

    /**
     * Assigns a new {@link AggregateRepository} instance to the test {@link #repository}.
     */
    public static void resetRepository() {
        repository = newRepository();
    }

    public static GivenAggregate givenAggregate() {
        return new GivenAggregate(repository);
    }

    public static ProjectId givenAggregateId(String id) {
        return ProjectId.newBuilder()
                        .setUuid(id)
                        .build();
    }

    public static ProjectAggregate givenStoredAggregate() {
        var id = Sample.messageOfType(ProjectId.class);
        var aggregate = givenAggregate().withUncommittedEvents(id);

        repository.storeAggregate(aggregate);
        return aggregate;
    }

    private static TestActorRequestFactory newRequestFactory() {
        var requestFactory =
                new TestActorRequestFactory(AggregateRepositoryTestEnv.class);
        return requestFactory;
    }

    private static ProjectAggregateRepository newRepository() {
        return new ProjectAggregateRepository();
    }
}
