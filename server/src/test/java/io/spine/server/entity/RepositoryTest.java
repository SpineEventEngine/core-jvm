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

import io.spine.core.TenantId;
import io.spine.core.Versions;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.entity.given.repository.ProjectEntity;
import io.spine.server.entity.given.repository.TestRepo;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.test.entity.ProjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.google.common.collect.Iterators.size;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`Repository` should")
class RepositoryTest {

    private BoundedContext context;
    private Repository<ProjectId, ProjectEntity> repository;
    private TenantId tenantId;

    private static ProjectId createId(String value) {
        return ProjectId.newBuilder()
                .setId(value)
                .build();
    }

    @BeforeEach
    void setUp() {
        context = BoundedContextBuilder
                .assumingTests(true)
                .build();
        repository = new TestRepo();
        context.internalAccess()
               .register(repository);
        tenantId = generate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context.isOpen()) {
            context.close();
        }
    }

    @Test
    @DisplayName("report unregistered on init")
    void beUnregisteredOnInit() {
        assertFalse(new TestRepo().hasContext());
    }

    @Test
    @DisplayName("not allow getting `BoundedContext` before registration")
    void notGetBcIfUnregistered() {
        assertThrows(IllegalStateException.class, () -> new TestRepo().context());
    }

    @Test
    @DisplayName("have no storage upon creation")
    void haveNoStorageOnCreation() {
        assertFalse(new TestRepo().storageAssigned());
    }

    @Test
    @DisplayName("prohibit obtaining unassigned storage")
    void notGetUnassignedStorage() throws Exception {
        context.close();
        assertThrows(IllegalStateException.class, () -> repository.storage());
    }

    @Test
    @DisplayName("init storage upon registration")
    void initStorageWithFactory() {
        assertTrue(repository.storageAssigned());
        assertNotNull(repository.storage());
    }

    @Test
    @DisplayName("provide default event filter")
    void provideDefaultEventFilter() {
        var filter = repository.eventFilter();
        assertNotNull(filter);
    }

    @Nested
    @DisplayName("prohibit overwriting already set context")
    class OverwritingContext {

        @Test
        @DisplayName("throwing `ISE`")
        void prohibit() {
            var anotherContext = BoundedContext
                    .singleTenant("Context-1")
                    .build();
            assertThrows(IllegalStateException.class, () ->
                    repository.registerWith(anotherContext));
        }

        @Test
        @DisplayName("allowing passing the same value twice")
        void idempotency() {
            // Previous value was set on registration.
            repository.registerWith(context);
            assertThat(repository.context())
                    .isEqualTo(context);
        }
    }

    @Test
    @DisplayName("close storage on close")
    void closeStorageOnClose() {
        var storage = (EntityRecordStorage<?, ?>) repository.storage();
        repository.close();

        assertTrue(storage.isClosed());
        assertFalse(repository.storageAssigned());
    }

    @Test
    @DisplayName("disconnect from storage on close")
    void disconnectStorageOnClose() {
        repository.close();
        assertFalse(repository.storageAssigned());
    }

    /**
     * Creates three entities in the repository.
     */
    private void createAndStoreEntities() {
        var op = new TenantAwareOperation(tenantId) {
            @Override
            public void run() {
                createAndStore("Eins");
                createAndStore("Zwei");
                createAndStore("Drei");
            }
        };
        op.execute();
    }

    @Test
    @DisplayName("iterate over entities")
    void iterateOverEntities() {
        createAndStoreEntities();
        var numEntities = size(iteratorAt(tenantId));
        assertEquals(3, numEntities);
    }

    @Test
    @DisplayName("not allow removal in entities iterator")
    void notAllowRemovalInIterator() {
        createAndStoreEntities();
        var iterator = iteratorAt(tenantId);
        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    @Test
    @DisplayName("register with a bounded context")
    void registered() {
        var repo = new TestRepo();

        assertFalse(repo.isRegistered());
        repo.checkNotRegistered();
        assertThrows(IllegalStateException.class, repo::checkRegistered);

        var context = BoundedContextBuilder
                .assumingTests()
                .build();
        repo.registerWith(context);

        assertTrue(repo.isRegistered());
        repo.checkRegistered();
        assertThrows(IllegalStateException.class, repo::checkNotRegistered);
    }

    @Test
    @DisplayName("register twice with the same context")
    void registerWithSameContext() {
        var repo = new TestRepo();

        var contextName = "test context";
        var context = BoundedContext.singleTenant(contextName).build();
        repo.registerWith(context);

        assertTrue(repo.isRegistered());

        repo.registerWith(context);
    }

    @Test
    @DisplayName("not register in another context")
    void anotherContext() {
        assertTrue(repository.isRegistered());
        var anotherContext = BoundedContext.singleTenant("another").build();
        assertThrows(IllegalStateException.class, () -> repository.registerWith(anotherContext));
    }

    private Iterator<ProjectEntity> iteratorAt(TenantId tenantId) {
        var result = TenantAwareRunner.with(tenantId)
                                      .evaluate(() -> repository.iterator(entity -> true));
        return result;
    }

    private void createAndStore(String idValue) {
        var id = createId(idValue);
        var entity = repository.create(id);
        var stateWithVersion = entity.state()
                .toBuilder()
                .setId(id)
                .build();
        TestTransaction.injectState(entity, stateWithVersion, Versions.zero());
        repository.store(entity);
    }
}
