/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import io.spine.base.AggregateState;
import io.spine.base.Identifier;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.aggregate.given.repo.GivenAggregate;
import io.spine.server.aggregate.given.repo.ProjectAggregateRepository;
import io.spine.server.entity.EntityRecord;
import io.spine.server.storage.AbstractStorageTest;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.testdata.Sample;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.Versions.zero;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * An abstract base for tests of {@link AggregateStorage} implementations.
 *
 * <p>Since the event-sourcing cutover, {@code AggregateStorage} keeps only the latest
 * Aggregate states; the journal of the emitted events is owned by the
 * {@link AggregateRepository} and covered by
 * {@code io.spine.server.entity.storage.EntityEventStorageSpec}.
 *
 * <p>The storage under test is produced by the
 * {@linkplain ServerEnvironment#storageFactory() current} {@code StorageFactory}. Descendants
 * cover a specific storage engine by configuring the corresponding factory before the suite runs;
 * with the default test configuration the suite runs against the in-memory storage.
 */
public abstract class AggregateStorageTest
        extends AbstractStorageTest<ProjectId,
                                    EntityRecord,
                                    AggregateStorage<ProjectId, AggProject>> {

    private final ProjectId id = Sample.messageOfType(ProjectId.class);

    private AggregateStorage<ProjectId, AggProject> storage;

    @Override
    @BeforeEach
    public void setUpAbstractStorageTest() {
        super.setUpAbstractStorageTest();
        ModelTests.dropAllModels();
        storage = storage();
    }

    @Override
    protected EntityRecord newStorageRecord(ProjectId id) {
        var state = AggProject.newBuilder()
                .setId(id)
                .build();
        return EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(id))
                .setState(AnyPacker.pack(state))
                .setVersion(zero())
                .build();
    }

    @Override
    protected ProjectId newId() {
        return ProjectId.generate();
    }

    @Override
    protected AggregateStorage<ProjectId, AggProject> newStorage() {
        return newStorage(TestAggregate.class);
    }

    /**
     * Creates the storage for the specified aggregate class.
     *
     * <p>The created storage should be closed manually.
     *
     * @param aggregateClass
     *         the aggregate class
     * @param <I>
     *         the type of aggregate IDs
     * @param <S>
     *         the type of aggregate states
     * @return a new storage instance
     */
    <I, S extends AggregateState<I>> AggregateStorage<I, S>
    newStorage(Class<? extends Aggregate<I, S, ?>> aggregateClass) {
        var spec = ContextSpec.singleTenant("`AggregateStorage` tests");
        var result =
                ServerEnvironment.instance()
                                 .storageFactory()
                                 .createAggregateStorage(spec, aggregateClass);
        return result;
    }

    @Test
    @DisplayName("return an empty `Optional` on reading an absent state")
    void absentRecord() {
        var record = storage.read(id);

        assertFalse(record.isPresent());
    }

    @Test
    @Override
    protected void immutableIndex() {
        var aggregate = givenAggregate().withUncommittedEvents();
        storage.writeState(aggregate);
        assertIndexImmutability();
    }

    @Test
    @Override
    protected void indexCountingAllIds() {
        var given = givenAggregate();
        var batchSize = 5;
        List<ProjectId> expectedIds = new ArrayList<>(batchSize);
        for(var index = 0; index < batchSize; index++) {
            var id = Sample.messageOfType(ProjectId.class);
            var aggregate = given.withUncommittedEvents(id);
            storage.writeState(aggregate);
            expectedIds.add(id);
        }

        var index = storage.index();
        var actualIds = ImmutableList.copyOf(index);
        assertThat(actualIds).containsExactlyElementsIn(expectedIds);
    }

    public static class TestAggregate extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

        protected TestAggregate(ProjectId id) {
            super(id);
        }
    }

    private static GivenAggregate givenAggregate() {
        var repository = new ProjectAggregateRepository();
        BoundedContextBuilder.assumingTests().add(repository).build();
        return new GivenAggregate(repository);
    }
}
