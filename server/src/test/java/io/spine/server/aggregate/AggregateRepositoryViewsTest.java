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

package io.spine.server.aggregate;

import io.spine.client.ActorRequestFactory;
import io.spine.grpc.StreamObservers;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.repo.AggregateWithLifecycle;
import io.spine.server.aggregate.given.repo.RepoOfAggregateWithLifecycle;
import io.spine.server.test.shared.LongIdAggregate;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`AggregateRepository` views should")
class AggregateRepositoryViewsTest {

    /** The Aggregate ID used in all tests. */
    private static final Long id = 100L;
    private final ActorRequestFactory requestFactory = new TestActorRequestFactory(getClass());
    private BoundedContext context;
    /**
     * The default behaviour of an {@code AggregateRepository}.
     */
    private AggregateRepository<Long, AggregateWithLifecycle, LongIdAggregate> repository;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // It's on purpose for tests.
    private Optional<AggregateWithLifecycle> aggregate;

    @BeforeEach
    void setUp() {
        context = BoundedContextBuilder
                .assumingTests()
                .build();
        repository = new RepoOfAggregateWithLifecycle();
        context.internalAccess()
               .register(repository);

        // Create the aggregate instance.
        postCommand("createCommand");
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    /**
     * Creates a command and posts it to {@code CommandBus}
     * for being processed by the repository.
     */
    private void postCommand(String cmd) {
        var command =
                requestFactory.command()
                              .create(RepoOfAggregateWithLifecycle.createCommandMessage(id, cmd));
        context.commandBus()
               .post(command, StreamObservers.noOpObserver());
    }

    @Test
    @DisplayName("find aggregate if no status flags are set")
    void findAggregatesWithNoStatus() {
        aggregate = repository.find(id);

        assertTrue(aggregate.isPresent());
        var agg = aggregate.get();
        assertFalse(agg.isArchived());
        assertFalse(agg.isDeleted());
    }

    @Test
    @DisplayName("find aggregates with `archived` status")
    void findArchivedAggregates() {
        postCommand("archive");

        aggregate = repository.find(id);

        assertTrue(aggregate.isPresent());
        var agg = aggregate.get();
        assertTrue(agg.isArchived());
        assertFalse(agg.isDeleted());
    }

    @Test
    @DisplayName("find aggregates with `deleted` status")
    void findDeletedAggregates() {
        postCommand("delete");

        aggregate = repository.find(id);

        assertTrue(aggregate.isPresent());
        var agg = aggregate.get();
        assertFalse(agg.isArchived());
        assertTrue(agg.isDeleted());
    }
}
