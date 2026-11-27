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
import io.spine.server.Application;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.entity.Repository;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.testing.server.blackbox.BlackBox;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;

import static io.spine.util.Cleanup.runKeepingFailure;
import static io.spine.util.Cleanup.throwIfAny;

/**
 * An abstract base for the tests performing actual delivery.
 *
 * <p>A test sets the {@link Delivery} of its contexts with {@link #use(Delivery)}, and then
 * builds the contexts with {@link #build(BoundedContextBuilder)} or
 * {@link #blackBox(BoundedContextBuilder)}. Each context is the only context of an application
 * of its own. The environments of these applications share the {@linkplain #nodeId() node}
 * and the {@code Delivery} of the test. Each application is given its own
 * {@linkplain #storageFactory() storage factory}, and closes that factory.
 *
 * <p>The {@code Delivery} is given a factory of its own. The test closes that factory
 * in {@link #tearDown()}, after the applications. The catch-up and inbox storages of
 * the delivery therefore stay open after a black box has closed its application.
 */
public abstract class AbstractDeliveryTest {

    /**
     * The identifier of the server node of the test.
     */
    private final NodeId nodeId = NodeId.newBuilder()
            .setValue(Identifier.newUuid())
            .build();

    /**
     * The delivery of the contexts built by the test, or {@code null} until it is set or
     * {@linkplain #delivery() created}.
     */
    private @Nullable Delivery delivery;

    /**
     * The applications built by the test.
     *
     * <p>A black box closes its application, and with it the storage factory of that
     * application. {@link #tearDown()} closes any application that is still open.
     */
    private final List<Application> applications = new ArrayList<>();

    /**
     * The storage factories given to a {@link Delivery}, closed by {@link #tearDown()}.
     */
    private final List<StorageFactory> deliveryFactories = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        delivery = null;
    }

    /**
     * Closes the applications that are still open, and then the storage factories
     * given to a {@link Delivery}.
     *
     * <p>An application closes the factory it was given. A tear-down that reads the
     * catch-up or inbox storage of the delivery does so before calling this method:
     * this method closes the delivery's factory. If closing an application fails,
     * the remaining applications and the delivery factories are still closed, and
     * the first failure is thrown.
     */
    @AfterEach
    public void tearDown() {
        Throwable failure = null;
        for (var application : applications) {
            failure = runKeepingFailure(failure, application::closeIfOpen);
        }
        applications.clear();
        for (var factory : List.copyOf(deliveryFactories)) {
            failure = runKeepingFailure(failure, () -> closeIfOpen(factory));
        }
        deliveryFactories.clear();
        delivery = null;
        throwIfAny(failure);
    }

    /**
     * Closes the factory if it is still open.
     */
    private static void closeIfOpen(StorageFactory factory) {
        if (factory.isOpen()) {
            factory.close();
        }
    }

    /**
     * Creates a new storage factory.
     *
     * <p>Returns a new in-memory factory on every call. Storage implementations override
     * this method and return a new factory on every call.
     *
     * <p>An application closes the factory it is given. {@link #deliveryFactory()}
     * uses this method for the factory of a {@link Delivery}, which the test closes.
     */
    public StorageFactory storageFactory() {
        return InMemoryStorageFactory.newInstance();
    }

    /**
     * Creates a storage factory for a {@link Delivery} and remembers it.
     *
     * <p>{@link #tearDown()} closes this factory after the applications. The catch-up
     * and inbox storages of the delivery stay open until then.
     */
    public final StorageFactory deliveryFactory() {
        var factory = storageFactory();
        deliveryFactories.add(factory);
        return factory;
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
            delivery = Delivery.local(nodeId, deliveryFactory());
        }
        return delivery;
    }

    /**
     * Creates a new environment with a new storage factory and the delivery of the test.
     *
     * <p>The application built with this environment closes the factory.
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
     *
     * <p>The test closes the application after it completes.
     */
    public final BoundedContext build(BoundedContextBuilder builder) {
        return newApplication(builder).contexts()
                                      .get(0);
    }

    /**
     * Creates a black box over the context of the given builder, the only context of
     * a new application with a {@linkplain #newEnvironment() new environment} of the test.
     *
     * <p>Closing the black box closes the application, and with it the storage factory
     * of that application.
     */
    public final BlackBox blackBox(BoundedContextBuilder builder) {
        return BlackBox.from(newApplication(builder));
    }

    /**
     * Creates a new application of the only context of the given builder,
     * with a {@linkplain #newEnvironment() new environment} of the test.
     *
     * <p>The test closes the application after it completes.
     */
    public final Application newApplication(BoundedContextBuilder builder) {
        var application = Application.newBuilder()
                .setServerEnvironment(newEnvironment())
                .add(builder)
                .build();
        applications.add(application);
        return application;
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
        var factory = deliveryFactory();
        use(Delivery.localWithShardsAndWindow(nodeId, factory, shards, Durations.ZERO));
    }
}
