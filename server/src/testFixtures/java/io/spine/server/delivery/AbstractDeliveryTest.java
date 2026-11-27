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
 * {@linkplain #newStorageFactory() storage factory}, and closes that factory.
 *
 * <p>The {@code Delivery} is given its own storage factory, for the catch-up and inbox
 * storages. The test closes that factory in {@link #tearDown()}, after the applications,
 * so those storages stay open after a black box has closed its application.
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
     * The storage factory for the catch-up and inbox storages of the {@link Delivery}
     * of this test, or {@code null} until {@link #deliveryStorageFactory()} creates it.
     *
     * <p>The test owns this factory and closes it in {@link #tearDown()}.
     */
    private @Nullable StorageFactory deliveryStorageFactory;

    @BeforeEach
    public void setUp() {
        delivery = null;
    }

    /**
     * Closes the applications that are still open, and then the storage factories
     * given to a {@link Delivery}.
     *
     * <p>An application closes the storage factory it was given. A tear-down that reads
     * the catch-up or inbox storage does so before calling this method: this method
     * closes the storage factory of the {@link Delivery}. If closing an application
     * fails, the remaining applications and that storage factory are still closed,
     * and the first failure is thrown.
     */
    @AfterEach
    public void tearDown() {
        Throwable failure = null;
        for (var application : applications) {
            failure = runKeepingFailure(failure, application::closeIfOpen);
        }
        applications.clear();
        var factory = deliveryStorageFactory;
        deliveryStorageFactory = null;
        if (factory != null) {
            failure = runKeepingFailure(failure, () -> closeIfOpen(factory));
        }
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
     * <p>Each call returns a new instance. The receiver owns that instance and closes it.
     * An application closes the storage factory given to its environment. The test
     * keeps the storage factory of its {@link Delivery} in {@link #deliveryStorageFactory()}
     * and closes that one instance in {@link #tearDown()}.
     *
     * <p>The default returns a new in-memory factory. Storage implementations override
     * this method and return a new factory on every call. This method does not
     * retain the instance.
     */
    protected StorageFactory newStorageFactory() {
        return InMemoryStorageFactory.newInstance();
    }

    /**
     * Obtains the {@link StorageFactory} for the catch-up and inbox storages
     * of the {@link Delivery} of this test.
     *
     * <p>The first call creates the factory with {@link #newStorageFactory()} and
     * keeps it. Later calls return that instance. The test owns it and closes it
     * in {@link #tearDown()}, after the applications.
     */
    public final StorageFactory deliveryStorageFactory() {
        if (deliveryStorageFactory == null) {
            deliveryStorageFactory = newStorageFactory();
        }
        return deliveryStorageFactory;
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
            delivery = Delivery.local(nodeId, deliveryStorageFactory());
        }
        return delivery;
    }

    /**
     * Creates a new environment with a new storage factory and the delivery of the test.
     *
     * <p>The application built with this environment closes the factory.
     */
    public final ServerEnvironment newEnvironment() {
        var storage = newStorageFactory();
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
     * <p>The test closes the application after it completes. If building the
     * application fails, closes the environment, and so the storage factory
     * given to it. The original failure is thrown. A failure of that cleanup
     * is added to it as suppressed.
     */
    public final Application newApplication(BoundedContextBuilder builder) {
        var environment = newEnvironment();
        try {
            var application = Application.newBuilder()
                    .setServerEnvironment(environment)
                    .add(builder)
                    .build();
            applications.add(application);
            return application;
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(environment, failure);
            throw failure;
        }
    }

    /**
     * Closes the environment whose application failed to build.
     *
     * <p>Closing the environment closes the storage factory given to it.
     * The application was not built, so {@link #tearDown()} would not see it.
     */
    private static void closeAfterFailure(ServerEnvironment environment, Throwable failure) {
        try {
            if (environment.isOpen()) {
                environment.close();
            }
        } catch (RuntimeException | Error closing) {
            if (closing != failure) {
                failure.addSuppressed(closing);
            }
        }
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
        var factory = deliveryStorageFactory();
        use(Delivery.localWithShardsAndWindow(nodeId, factory, shards, Durations.ZERO));
    }
}
