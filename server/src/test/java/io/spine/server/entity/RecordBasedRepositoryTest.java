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

package io.spine.server.entity;

import com.google.common.collect.Lists;
import com.google.common.truth.OptionalSubject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.client.ArchivedColumn;
import io.spine.client.CompositeFilter;
import io.spine.client.IdFilter;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.server.entity.given.repository.GivenLifecycleFlags;
import io.spine.testing.TestValues;
import io.spine.testing.server.model.ModelTests;
import io.spine.testing.server.tenant.TenantAwareTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.reverse;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.FieldMaskUtil.fromFieldNumbers;
import static io.spine.client.CompositeFilter.CompositeOperator.ALL;
import static io.spine.client.Filters.all;
import static io.spine.client.Filters.eq;
import static io.spine.client.OrderBy.Direction.ASCENDING;
import static io.spine.client.OrderBy.Direction.DESCENDING;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.entity.TestTransaction.archive;
import static io.spine.server.entity.TestTransaction.delete;
import static io.spine.server.entity.given.RecordBasedRepositoryTestEnv.assertMatches;
import static io.spine.server.entity.given.RecordBasedRepositoryTestEnv.emptyFormat;
import static io.spine.server.entity.given.RecordBasedRepositoryTestEnv.orderByName;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The abstract test for the {@linkplain RecordBasedRepository} derived classes.
 *
 * @param <E>
 *         the type of the {@link Entity} of this repository
 */
public abstract
class RecordBasedRepositoryTest<E extends AbstractEntity<I, S>, I, S extends EntityState<I>>
        extends TenantAwareTest {

    private RecordBasedRepository<I, E, S> repository;

    protected abstract RecordBasedRepository<I, E, S> createRepository();

    protected abstract E createEntity(I id);

    protected final E createEntity(int idValue) {
        var id = createId(idValue);
        return createEntity(id);
    }

    protected abstract List<E> createEntities(int count);

    /**
     * Creates the entities using the supplied names for entities
     * {@link io.spine.server.entity.given.RecordBasedRepositoryTestEnv#ENTITY_NAME_COLUMN "name"}
     * state property.
     */
    protected abstract List<E> createWithNames(int count, Supplier<String> nameSupplier);

    /**
     * Orders the entities by the
     * {@link io.spine.server.entity.given.RecordBasedRepositoryTestEnv#ENTITY_NAME_COLUMN "name"}
     * state property.
     */
    protected abstract List<E> orderedByName(List<E> entities);

    protected abstract I createId(int value);

    /**
     * Sets the {@code package-local} {@link AbstractEntity#state() state} property of an entity.
     */
    protected void setEntityState(E entity, S state) {
        entity.setState(state);
    }

    @BeforeEach
    protected void setUp() {
        ModelTests.dropAllModels();
        this.repository = createRepository();
        setCurrentTenant(generate());
    }

    @AfterEach
    protected void tearDown() throws Exception {
        clearCurrentTenant();
        ModelTests.dropAllModels();
    }

    /*
     * Store/load functions for working in multi-tenant execution context
     **********************************************************************/

    private void storeEntity(E entity) {
        repository().store(entity);
    }

    @CanIgnoreReturnValue
    private List<E> createAndStoreEntities(RecordBasedRepository<I, E, S> repo, int count) {
        var entities = createEntities(count);
        storeEntities(repo, entities);
        return entities;
    }

    private void storeEntities(RecordBasedRepository<I, E, S> repo, List<E> entities) {
        for (var entity : entities) {
            repo.store(entity);
        }
    }

    private Iterator<E> loadMany(List<I> ids) {
        return repository().loadAll(ids, FieldMask.getDefaultInstance());
    }

    private E loadOrCreate(I id) {
        return repository().findOrCreate(id);
    }

    /*
     * Tests
     ************/

    @Test
    @DisplayName("create entities")
    void createEntities() {
        var id = createId(5);
        var projectEntity = repository().create(id);
        assertNotNull(projectEntity);
        assertEquals(id, projectEntity.id());
    }

    protected RecordBasedRepository<I, E, S> repository() {
        return repository;
    }

    @Nested
    @DisplayName("find one entity")
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    class FindOne {

        private E entity;

        @BeforeEach
        void createTheEntity() {
            entity = createEntity(TestValues.random(1000));
        }

        @Test
        @DisplayName("by ID")
        void byId() {
            storeEntity(entity);
            assertFound();
        }

        @Test
        @DisplayName("by ID if archived")
        void byIdArchived() {
            archive(asTxEntity(entity));
            storeEntity(entity);
            assertFound();
        }

        @Test
        @DisplayName("by ID if deleted")
        void byIdDeleted() {
            delete(asTxEntity(entity));
            storeEntity(entity);
            assertFound();
        }

        private void assertFound() {
            assertResult(find(entity.id()));
        }

        private void assertResult(Optional<E> optional) {
            var assertResult = assertThat(optional);
            assertResult.isPresent();
            assertResult.hasValue(entity);
        }

        private Optional<E> find(I id) {
            return repository().find(id);
        }
    }

    @Nested
    @DisplayName("find multiple entities")
    class FindMultiple {

        private Iterator<EntityRecord> loadAllRecords() {
            return repository.findRecords(ResponseFormat.getDefaultInstance());
        }

        @Test
        @DisplayName("by IDs")
        void multipleEntitiesByIds() {
            var count = 10;
            var entities = createAndStoreEntities(repository(), count);

            List<I> ids = Lists.newLinkedList();

            // Find some of the records (half of them in this case)
            for (var i = 0; i < count / 2; i++) {
                ids.add(entities.get(i)
                                .id());
            }

            Collection<E> found = newArrayList(loadMany(ids));

            assertThat(found).hasSize(ids.size());
            assertThat(entities).containsAtLeastElementsIn(found);
        }

        @Test
        @DisplayName("by query")
        void entitiesByQuery() {
            var id1 = createId(271);
            var id2 = createId(314);
            var entity1 = createEntity(id1);
            var entity2 = createEntity(id2);
            repository().store(entity1);
            repository().store(entity2);

            var fieldPath = "id_string";
            var fieldValue = StringValue.newBuilder()
                    .setValue(id1.toString())
                    .build();
            var filter = eq(fieldPath, fieldValue);
            var aggregatingFilter = CompositeFilter.newBuilder()
                    .addFilter(filter)
                    .setOperator(ALL)
                    .build();
            var filters = TargetFilters.newBuilder()
                    .addFilter(aggregatingFilter)
                    .build();
            Collection<E> found = newArrayList(repository().find(filters, emptyFormat()));

            var assertThatFound = assertThat(found);
            assertThatFound.hasSize(1);
            assertThatFound.contains(entity1);
            assertThatFound.doesNotContain(entity2);
        }

        @Test
        @DisplayName("by query and field mask")
        void entitiesByQueryAndFields() {
            var count = 10;
            var entities = createAndStoreEntities(repository(), count);

            // Find some of the entities (half of them in this case).
            var idsToObtain = count / 2;
            var ids = obtainSomeNumberOfEntityIds(entities, idsToObtain);

            var filters = createIdFilters(ids);
            var firstFieldOnly = createFirstFieldOnlyMask(entities);
            var readEntities = find(filters, firstFieldOnly);
            Collection<E> foundList = newArrayList(readEntities);

            assertThat(foundList).hasSize(ids.size());
            for (var entity : foundList) {
                assertMatches(entity, firstFieldOnly);
            }
        }

        @Test
        @DisplayName("in ascending order")
        void entitiesInAscendingOrder() {
            var count = 10;
            // UUIDs are used to produce a collection with the names in a random order.
            var entities = entitiesWithNames(repository(), count, Identifier::newUuid);

            var format = ResponseFormat.newBuilder()
                    .addOrderBy(orderByName(ASCENDING))
                    .build();
            var readEntities = repository().loadAll(format);
            Collection<E> foundList = newArrayList(readEntities);

            var expectedList = orderedByName(entities);
            assertThat(foundList).containsExactlyElementsIn(expectedList);
        }

        @Test
        @DisplayName("in descending order")
        void entitiesInDescendingOrder() {
            var count = 10;
            // UUIDs are used to produce a collection with the names in a random order.
            var entities = entitiesWithNames(repository(), count, Identifier::newUuid);

            var format = ResponseFormat.newBuilder()
                    .addOrderBy(orderByName(DESCENDING))
                    .build();
            var readEntities = repository().loadAll(format);
            Collection<E> foundList = newArrayList(readEntities);

            var expectedList = reverse(orderedByName(entities));
            assertThat(foundList).containsExactlyElementsIn(expectedList);
        }

        @Test
        @DisplayName("limited number of entities")
        void limitedNumberOfEntities() {
            var totalCount = 10;
            var limit = 5;
            // UUIDs are used to produce a collection with the names in a random order.
            var entities = entitiesWithNames(repository(), totalCount, Identifier::newUuid);

            var format = ResponseFormat.newBuilder()
                    .addOrderBy(orderByName(ASCENDING))
                    .setLimit(limit)
                    .build();
            var readEntities = repository().loadAll(format);
            Collection<E> foundList = newArrayList(readEntities);

            var expectedList = orderedByName(entities).subList(0, limit);
            assertThat(foundList).containsExactlyElementsIn(expectedList);
        }

        @Test
        @DisplayName("all entities")
        void allEntities() {
            var entities = createAndStoreEntities(repository(), 150);
            Collection<E> found = newArrayList(loadAll());

            var assertFoundEntities = assertThat(found);

            assertFoundEntities.hasSize(entities.size());

            assertThat(entities).containsExactlyElementsIn(found);
        }

        @Test
        @DisplayName("all entity records")
        void allEntityRecords() {
            var entities = createAndStoreEntities(repository, 150);
            Collection<EntityRecord> found = newArrayList(loadAllRecords());
            assertThat(found).hasSize(entities.size());

            for (var entity : entities) {
                var record = repository.toRecord(entity);
                assertThat(found).contains(record.record());
            }
        }

        @Test
        @DisplayName("no entities if repository is empty")
        void noEntitiesIfEmpty() {
            Collection<E> found = newArrayList(loadAll());
            assertThat(found).isEmpty();
        }

        private Iterator<E> find(TargetFilters filters, FieldMask firstFieldOnly) {
            var format = ResponseFormat.newBuilder()
                    .setFieldMask(firstFieldOnly)
                    .build();
            return repository().find(filters, format);
        }

        private List<E> entitiesWithNames(RecordBasedRepository<I, E, S> repo, int count,
                                          Supplier<String> nameSupplier) {
            var entities = createWithNames(count, nameSupplier);
            storeEntities(repo, entities);
            return entities;
        }

        private List<Any> obtainSomeNumberOfEntityIds(List<E> entities, int count) {
            List<Any> ids = Lists.newLinkedList();
            for (var i = 0; i < count; i++) {
                var entityId = (Message) entities.get(i)
                                                 .id();
                var id = pack(entityId);
                ids.add(id);
            }
            return ids;
        }

        private TargetFilters createIdFilters(List<Any> ids) {
            var filter = IdFilter.newBuilder()
                    .addAllId(ids)
                    .build();
            var filters = TargetFilters.newBuilder()
                    .setIdFilter(filter)
                    .build();
            return filters;
        }

        private FieldMask createFirstFieldOnlyMask(List<E> entities) {
            var firstEntity = entities.get(0);
            var fieldMask = fromFieldNumbers(firstEntity.defaultState()
                                                        .getClass(), 1);
            return fieldMask;
        }

        private Iterator<E> loadAll() {
            return repository().loadAll(ResponseFormat.getDefaultInstance());
        }
    }

    @Test
    @DisplayName("create entity on `loadOrCreate` if not found")
    void loadOrCreateEntity() {
        var count = 3;
        createAndStoreEntities(repository(), count);

        var id = createId(count + 1);
        var entity = loadOrCreate(id);

        assertNotNull(entity);
        assertEquals(id, entity.id());
    }

    @Test
    @DisplayName("handle wrong passed IDs")
    void handleWrongPassedIds() {
        var count = 10;
        var entities = createAndStoreEntities(repository(), count);
        List<I> ids = Lists.newLinkedList();
        for (var i = 0; i < count; i++) {
            ids.add(entities.get(i)
                            .id());
        }
        Entity<I, S> sideEntity = createEntity(375);
        ids.add(sideEntity.id());

        Collection<E> found = newArrayList(loadMany(ids));
        var assertThatFound = assertThat(found);
        assertThatFound.hasSize(ids.size() - 1); // Check we've found all existing items
        assertThatFound.containsExactlyElementsIn(entities);
    }

    @Nested
    @DisplayName("mark records")
    class MarkRecords {

        @Test
        @DisplayName("archived")
        void archived() {
            var entity = createEntity(821);
            var id = entity.id();

            storeEntity(entity);

            assertFound(id).isPresent();

            entity.setLifecycleFlags(GivenLifecycleFlags.archived());
            storeEntity(entity);

            assertFound(id).isEmpty();
        }

        @Test
        @DisplayName("deleted")
        void deleted() {
            var entity = createEntity(822);
            var id = entity.id();

            storeEntity(entity);

            assertFound(id).isPresent();

            entity.setLifecycleFlags(GivenLifecycleFlags.deleted());
            storeEntity(entity);

            assertFound(id).isEmpty();
        }

        private OptionalSubject assertFound(I id) {
            var entity = repository().findActive(id);
            return assertThat(entity);
        }
    }

    @Test
    @DisplayName("exclude non-active records from entity query")
    void excludeNonActiveRecords() {
        var activeEntity = createEntity(271);
        var archivedEntity = createEntity(42);
        var deletedEntity = createEntity(314);
        delete(asTxEntity(deletedEntity));
        archive(asTxEntity(archivedEntity));

        // Fill the storage
        storeEntity(activeEntity);
        storeEntity(archivedEntity);
        storeEntity(deletedEntity);

        var found = repository().loadAll(emptyFormat());
        List<E> foundList = newArrayList(found);
        // Check results
        assertThat(foundList).hasSize(1);
        var actualEntity = foundList.get(0);
        assertEquals(activeEntity, actualEntity);
    }

    @Test
    @DisplayName("allow any lifecycle if column is involved in query")
    void ignoreLifecycleForColumns() {
        var activeEntity = createEntity(42);
        var archivedEntity = createEntity(314);
        var deletedEntity = createEntity(271);
        delete(asTxEntity(deletedEntity));
        archive(asTxEntity(archivedEntity));

        // Fill the storage
        storeEntity(activeEntity);
        storeEntity(archivedEntity);
        storeEntity(deletedEntity);

        var filter = all(eq(ArchivedColumn.instance(), false));
        var filters = TargetFilters.newBuilder()
                .addFilter(filter)
                .build();

        var found = repository().find(filters, emptyFormat());
        Collection<E> foundList = newArrayList(found);
        // Check result
        var assertFoundList = assertThat(foundList);
        assertFoundList.hasSize(2);
        assertFoundList.containsExactly(activeEntity, deletedEntity);
    }

    private TransactionalEntity<?, ?, ?> asTxEntity(E entity) {
        return (TransactionalEntity<?, ?, ?>) entity;
    }
}
