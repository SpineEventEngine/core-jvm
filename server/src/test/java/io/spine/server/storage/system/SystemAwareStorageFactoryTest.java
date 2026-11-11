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

package io.spine.server.storage.system;

import com.google.common.testing.NullPointerTester;
import io.spine.environment.DefaultMode;
import io.spine.environment.Environment;
import io.spine.environment.Tests;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.event.store.EmptyEventStore;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.system.given.MemoizingStorageFactory;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.system.server.SystemBoundedContexts.systemOf;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`SystemAwareStorageFactory` should")
class SystemAwareStorageFactoryTest {

    private static final ContextSpec CONTEXT = ContextSpec.multiTenant("foo");

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void nulls() {
        new NullPointerTester()
                .testAllPublicStaticMethods(SystemAwareStorageFactory.class);
    }

    @Test
    @DisplayName("wrap DefaultMode storage")
    void wrapProdStorage() {
        Environment.instance()
                   .setTo(DefaultMode.class);

        StorageFactory productionStorage = new MemoizingStorageFactory();
        var serverEnv = ServerEnvironment.newBuilder()
                .under(DefaultMode.class, env -> env.use(productionStorage))
                .build();
        var storageFactory = serverEnv.storageFactory();
        assertThat(storageFactory)
                .isInstanceOf(SystemAwareStorageFactory.class);
        var systemAware = (SystemAwareStorageFactory) storageFactory;
        assertThat(systemAware.delegate())
                .isEqualTo(productionStorage);

        Environment.instance()
                   .reset();
    }

    @Test
    @DisplayName("wrap test storage")
    void wrapTestStorage() {
        StorageFactory testStorage = InMemoryStorageFactory.newInstance();
        var serverEnv = ServerEnvironment.newBuilder()
                .under(Tests.class, env -> env.use(testStorage))
                .build();
        var storageFactory = serverEnv.storageFactory();
        assertThat(storageFactory).isInstanceOf(SystemAwareStorageFactory.class);
        var systemAware = (SystemAwareStorageFactory) storageFactory;
        assertThat(systemAware.delegate())
                .isEqualTo(testStorage);
    }

    @Test
    @DisplayName("delegate record storage creation to given factory")
    void delegateRecordStorage() {
        var factory = new MemoizingStorageFactory();
        var systemAware = SystemAwareStorageFactory.wrap(factory);
        var recordType = Project.class;
        var spec = new RecordSpec<>(ProjectId.class, Project.class,
                                    i -> ProjectId.getDefaultInstance());
        var storage = systemAware.createRecordStorage(CONTEXT, spec);
        assertThat(storage).isNull();
        assertThat(factory.requestedStorages())
                .containsExactly(recordType);
    }

    @Test
    @DisplayName("delegate inbox storage creation to given factory")
    void delegateInboxStorage() {
        var factory = new MemoizingStorageFactory();
        var systemAware = SystemAwareStorageFactory.wrap(factory);
        var storage = systemAware.createInboxStorage(CONTEXT.isMultiTenant());
        assertThat(storage).isNull();
        assertTrue(factory.requestedInbox());
    }

    @Test
    @DisplayName("delegate catch-up storage creation to given factory")
    void delegateCatchUpStorage() {
        var factory = new MemoizingStorageFactory();
        var systemAware = SystemAwareStorageFactory.wrap(factory);
        var storage = systemAware.createCatchUpStorage(CONTEXT.isMultiTenant());
        assertThat(storage).isNull();
        assertTrue(factory.requestedCatchUp());
    }

    @Test
    @DisplayName("delegate EventStore creation to given factory")
    void delegateNormalEventStore() {
        var factory = new MemoizingStorageFactory();
        var systemAware = SystemAwareStorageFactory.wrap(factory);
        var store = systemAware.createEventStore(CONTEXT);
        assertThat(store).isNull();
        assertTrue(factory.requestedEventStore());
    }

    @Test
    @DisplayName("create `EmptyEventStore` if event persistence is disabled")
    void createEmptyEventStore() {
        var factory = new MemoizingStorageFactory();
        var systemAware = SystemAwareStorageFactory.wrap(factory);
        var name = CONTEXT.name()
                          .getValue();
        var context = BoundedContextBuilder.assumingTests(name, /* multiTenant = */ true)
                .build();
        var systemContext = systemOf(context);
        var systemSpec = systemContext.spec();
        assertFalse(systemSpec.storesEvents());
        var store = systemAware.createEventStore(systemSpec);
        assertFalse(factory.requestedEventStore());
        var assertStore = assertThat(store);
        assertStore.isNotNull();
        assertStore.isInstanceOf(EmptyEventStore.class);
    }

    @Test
    @DisplayName("wrap other factories only once")
    void wrapIdempotently() {
        var factory = new MemoizingStorageFactory();
        var wrapped = SystemAwareStorageFactory.wrap(factory);
        var wrappedTwice = SystemAwareStorageFactory.wrap(wrapped);
        assertThat(wrappedTwice).isEqualTo(wrapped);
        assertThat(wrappedTwice.delegate()).isEqualTo(factory);
    }

    @Test
    @DisplayName("close delegate")
    void close() throws Exception {
        var factory = new MemoizingStorageFactory();
        var wrapped = SystemAwareStorageFactory.wrap(factory);
        assertFalse(factory.isClosed());
        wrapped.close();
        assertTrue(factory.isClosed());
    }
}
