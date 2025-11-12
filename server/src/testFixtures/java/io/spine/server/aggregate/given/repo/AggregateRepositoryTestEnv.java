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

package io.spine.server.aggregate.given.repo;

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.test.aggregate.ProjectId;
import io.spine.testdata.Sample;
import io.spine.testing.client.TestActorRequestFactory;

public class AggregateRepositoryTestEnv {

    private static final TestActorRequestFactory requestFactory = newRequestFactory();
    private static BoundedContext context = newBoundedContext();
    private static ProjectAggregateRepository repository = newRepository();

    /** Prevent instantiation of this utility class. */
    private AggregateRepositoryTestEnv() {
    }

    public static TestActorRequestFactory requestFactory() {
        return requestFactory;
    }

    public static BoundedContext context() {
        return context;
    }

    public static ProjectAggregateRepository repository() {
        return repository;
    }

    /**
     * Assigns a new {@link BoundedContext} instance to the test {@link #context}.
     */
    public static void resetBoundedContext() {
        context = newBoundedContext();
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

    public static void givenStoredAggregateWithId(String id) {
        var projectId = givenAggregateId(id);
        var aggregate = givenAggregate().withUncommittedEvents(projectId);

        repository.storeAggregate(aggregate);
    }

    private static TestActorRequestFactory newRequestFactory() {
        var requestFactory =
                new TestActorRequestFactory(AggregateRepositoryTestEnv.class);
        return requestFactory;
    }

    private static BoundedContext newBoundedContext() {
        var context = BoundedContextBuilder.assumingTests().build();
        return context;
    }

    private static ProjectAggregateRepository newRepository() {
        return new ProjectAggregateRepository();
    }
}
