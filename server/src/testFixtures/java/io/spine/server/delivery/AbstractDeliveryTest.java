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

package io.spine.server.delivery;

import com.google.protobuf.util.Durations;
import io.spine.base.Identifier;
import io.spine.environment.Tests;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.entity.Repository;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.testing.server.blackbox.BlackBox;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static io.spine.testing.server.TestContexts.buildForTests;

/**
 * An abstract base for the tests performing actual delivery.
 *
 * <p>A test sets the {@link Delivery} of its contexts with {@link #use(Delivery)}, and then
 * builds the contexts with {@link #build(BoundedContextBuilder)} or
 * {@link #blackBox(BoundedContextBuilder)}. Each context is the only context of an application
 * of its own. The environments of these applications share the {@linkplain #storageFactory()
 * storage factory}, the {@linkplain #nodeId() node}, and the {@code Delivery} of the test.
 *
 * <p>The environments of a test are not closed with its contexts: closing a context or
 * a black box leaves the storage factory of the test open.
 */
public abstract class AbstractDeliveryTest {

    /**
     * The identifier of the server node of the test.
     */
    private final NodeId nodeId = NodeId.newBuilder()
            .setValue(Identifier.newUuid())
            .build();

    /**
     * The storage factory of the test, created on the first call to {@link #storageFactory()}.
     */
    private @MonotonicNonNull StorageFactory inMemoryStorage;

    /**
     * The delivery of the contexts built by the test, or {@code null} until it is set or
     * {@linkplain #delivery() created}.
     */
    private @Nullable Delivery delivery;

    @BeforeEach
    public void setUp() {
        delivery = null;
    }

    @AfterEach
    public void tearDown() {
        delivery = null;
    }

    /**
     * Obtains the storage factory with which the contexts and the delivery of the test create
     * their storages.
     *
     * <p>Returns an in-memory factory by default. Storage implementations override this method
     * to run the tests over their storages.
     */
    public StorageFactory storageFactory() {
        if (inMemoryStorage == null) {
            inMemoryStorage = InMemoryStorageFactory.newInstance();
        }
        return inMemoryStorage;
    }

    /**
     * Obtains the identifier of the server node of the test.
     */
    public final NodeId nodeId() {
        return nodeId;
    }

    /**
     * Sets the delivery of the contexts built by the test afterwards.
     */
    public final void use(Delivery delivery) {
        this.delivery = delivery;
    }

    /**
     * Obtains the delivery of the contexts built by the test.
     *
     * <p>Unless {@linkplain #use(Delivery) set}, creates a local delivery on the first call.
     */
    public final Delivery delivery() {
        if (delivery == null) {
            delivery = Delivery.local(nodeId, storageFactory());
        }
        return delivery;
    }

    /**
     * Creates a new environment with the storage factory and the delivery of the test.
     */
    public final ServerEnvironment newEnvironment() {
        var storage = storageFactory();
        var delivery = delivery();
        return ServerEnvironment.newBuilder()
                .under(Tests.class, env -> env.use(storage)
                                              .use(delivery))
                .build();
    }

    /**
     * Builds the context of the given builder as the only context of a new application,
     * with a {@linkplain #newEnvironment() new environment} of the test.
     */
    public final BoundedContext build(BoundedContextBuilder builder) {
        return buildForTests(builder, newEnvironment());
    }

    /**
     * Creates a black box over the context of the given builder,
     * {@linkplain #build(BoundedContextBuilder) built} with a new environment of the test.
     *
     * <p>Closing the black box closes only the context.
     */
    public final BlackBox blackBox(BoundedContextBuilder builder) {
        return BlackBox.from(build(builder));
    }

    /**
     * Creates a black box over a single-tenant test context with the given components.
     *
     * <p>The components can be either {@link Repository} instances or entity classes.
     *
     * @see #blackBox(BoundedContextBuilder)
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) // Entity classes are checked by the builder.
    public final BlackBox blackBoxWith(Object... components) {
        var builder = BoundedContextBuilder.assumingTests();
        for (var component : components) {
            if (component instanceof Repository<?, ?> repository) {
                builder.add(repository);
            } else {
                builder.add((Class) component);
            }
        }
        return blackBox(builder);
    }

    /**
     * Makes the contexts built afterwards deliver through a local delivery
     * with the given number of shards.
     */
    public final void changeShardCountTo(int shards) {
        use(Delivery.localWithShardsAndWindow(nodeId, storageFactory(), shards, Durations.ZERO));
    }
}
