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

package io.spine.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.BoundedContextName;
import io.spine.logging.WithLogging;
import io.spine.server.aggregate.AggregateRootDirectory;
import io.spine.server.aggregate.InMemoryRootDirectory;
import io.spine.server.bus.BusFilter;
import io.spine.server.bus.Listener;
import io.spine.server.bus.MessageDispatcher;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.commandbus.CommandDispatcherDelegate;
import io.spine.server.commandbus.CommandScheduler;
import io.spine.server.delivery.Delivery;
import io.spine.server.enrich.Enricher;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Repository;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventDispatcher;
import io.spine.server.event.EventEnricher;
import io.spine.server.stand.Stand;
import io.spine.server.storage.StorageFactory;
import io.spine.server.trace.TracerFactory;
import io.spine.server.transport.TransportFactory;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.NoOpSystemClient;
import io.spine.system.server.SystemClient;
import io.spine.system.server.SystemContext;
import io.spine.system.server.SystemSettings;
import io.spine.system.server.SystemWriteSide;
import io.spine.type.MessageClass;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.core.BoundedContextNames.assumingTestsValue;
import static io.spine.server.ContextSpec.multiTenant;
import static io.spine.server.ContextSpec.singleTenant;
import static io.spine.util.Cleanup.runKeepingFailure;
import static java.lang.String.format;

/**
 * A builder for producing {@code BoundedContext} instances.
 *
 * <p>The contexts are built by the {@link Application} to which the builders are
 * {@linkplain Application.Builder#add(BoundedContextBuilder) added}. The application gives
 * each context the technologies of its {@link ServerEnvironment}.
 *
 * <p>A builder created for tests, by one of the {@code assumingTests()} methods, can also
 * {@linkplain #build() build} its context by itself.
 */
@SuppressWarnings("ClassWithTooManyMethods" /* OK for this central piece. */)
public final class BoundedContextBuilder implements WithLogging {

    /**
     * Counts the builders of test-only Bounded Contexts created in this JVM without a name.
     *
     * <p>Gives each of these contexts a name of its own, because two contexts of one
     * application cannot have one name.
     */
    private static final AtomicLong testContextCounter = new AtomicLong();

    private final ContextSpec spec;

    /**
     * Tells if this builder was created for a test-only context.
     *
     * <p>Only such a builder can {@linkplain #build() build} a context by itself.
     */
    private final boolean forTests;

    private final CommandBus.Builder commandBus = CommandBus.newBuilder();

    /**
     * Command dispatchers to be registered with the context {@link CommandBus}
     * after the Bounded Context creation.
     */
    private final Collection<CommandDispatcher> commandDispatchers = new ArrayList<>();

    private final EventBus.Builder eventBus = EventBus.newBuilder();

    /**
     * Event dispatchers to be registered with the context {@link EventBus} and/or
     * {@link io.spine.server.integration.IntegrationBroker IntegrationBroker} after
     * the Bounded Context creation.
     */
    private final Collection<EventDispatcher> eventDispatchers = new ArrayList<>();

    private final SystemSettings systemSettings;

    /**
     * The factory with which the repositories of the built context create their storages.
     *
     * <p>If not set, the factory {@linkplain ServerEnvironment#storageFactory() configured} in
     * the server environment of the application is used.
     */
    private @Nullable StorageFactory storageFactory;

    /**
     * The delivery in which the repositories of the built context register their inboxes.
     *
     * <p>If not set, the delivery {@linkplain ServerEnvironment#delivery() of} the server
     * environment is used.
     */
    private @Nullable Delivery delivery;

    /**
     * Creates the scheduler of the commands posted to the built context.
     *
     * <p>If not set, the context obtains its scheduler
     * {@linkplain ServerEnvironment#newCommandScheduler() from} the server environment
     * of the application.
     */
    private @Nullable Supplier<CommandScheduler> commandSchedulerFactory;

    /**
     * The transport through which the built context exchanges messages with other contexts.
     *
     * <p>If not set, the factory {@linkplain ServerEnvironment#transportFactory() configured} in
     * the server environment of the application is used.
     */
    private @Nullable TransportFactory transportFactory;

    /**
     * The factory of the tracers for the messages of the built context.
     *
     * <p>If not set, the factory {@linkplain ServerEnvironment#tracing() configured} in
     * the server environment of the application is used, if any.
     */
    private @Nullable TracerFactory tracerFactory;

    private @MonotonicNonNull Stand stand;
    @SuppressWarnings("deprecation") // The `AggregateRootDirectory` API is deprecated.
    private @MonotonicNonNull Supplier<AggregateRootDirectory> rootDirectory;

    /** Repositories to be registered with the Bounded Context being built after its creation. */
    private final Collection<Repository<?, ?>> repositories = new ArrayList<>();

    private @Nullable Consumer<BoundedContext> onBeforeClose = null;

    /**
     * Creates a new builder with the given spec.
     *
     * @param spec
     *         the context spec for the built context
     * @see BoundedContext#singleTenant
     * @see BoundedContext#multiTenant
     */
    BoundedContextBuilder(ContextSpec spec) {
        this(spec, SystemSettings.defaults(), /* forTests = */ false);
    }

    /**
     * Creates a new builder with the given spec and system features.
     *
     * @param spec
     *         the context spec for the built context
     * @param systemSettings
     *         settings of the System context; may be changed later via {@link #systemSettings()}
     * @param forTests
     *         tells if the builder is for a test-only context, which the builder
     *         can {@linkplain #build() build} by itself
     * @see BoundedContext#singleTenant
     * @see BoundedContext#multiTenant
     */
    private BoundedContextBuilder(ContextSpec spec,
                                  SystemSettings systemSettings,
                                  boolean forTests) {
        this.spec = checkNotNull(spec);
        this.systemSettings = checkNotNull(systemSettings);
        this.forTests = forTests;
    }

    /**
     * Creates a new builder for a test-only Bounded Context.
     *
     * <p>The name of the context is {@code AssumingTests} followed by a number, such as
     * {@code AssumingTests-1}. Each call gives a new number, so that several such contexts
     * can be open at once.
     *
     * <p>The builder can {@linkplain #build() build} the context by itself.
     *
     * @param multiTenant
     *         tells if the context is multi-tenant
     */
    @Internal
    @VisibleForTesting
    public static BoundedContextBuilder assumingTests(boolean multiTenant) {
        var name = assumingTestsValue() + '-' + testContextCounter.incrementAndGet();
        return assumingTests(name, multiTenant);
    }

    /**
     * Creates a new builder for a single-tenant test-only Bounded Context.
     *
     * <p>The name of the context is {@code AssumingTests} followed by a number, such as
     * {@code AssumingTests-1}.
     *
     * <p>The builder can {@linkplain #build() build} the context by itself.
     */
    @Internal
    @VisibleForTesting
    public static BoundedContextBuilder assumingTests() {
        return assumingTests(/* multiTenant = */ false);
    }

    /**
     * Creates a new builder for a single-tenant test-only Bounded Context with the given name.
     *
     * <p>The builder can {@linkplain #build() build} the context by itself.
     *
     * @param name
     *         the name of the context
     */
    @Internal
    @VisibleForTesting
    public static BoundedContextBuilder assumingTests(String name) {
        return assumingTests(name, /* multiTenant = */ false);
    }

    /**
     * Creates a new builder for a test-only Bounded Context with the given name.
     *
     * <p>The builder can {@linkplain #build() build} the context by itself.
     *
     * @param name
     *         the name of the context
     * @param multiTenant
     *         tells if the context is multi-tenant
     */
    @Internal
    @VisibleForTesting
    public static BoundedContextBuilder assumingTests(String name, boolean multiTenant) {
        checkNotNull(name);
        var spec = multiTenant
                   ? multiTenant(name)
                   : singleTenant(name);
        return new BoundedContextBuilder(spec, SystemSettings.defaults(), /* forTests = */ true);
    }

    /**
     * Obtains the context spec.
     */
    public ContextSpec spec() {
        return spec;
    }

    /**
     * Returns the name of the resulting context.
     */
    public BoundedContextName name() {
        return spec.name();
    }

    /**
     * Tells whether this builder is creating a multi-tenant Bounded Context.
     */
    public boolean isMultiTenant() {
        return spec.isMultiTenant();
    }

    /**
     * Obtains the instance of the {@code Stand} for the context to be built.
     *
     * <p>This method must be invoked only from the
     * {@link BoundedContext#BoundedContext(BoundedContextBuilder) constructor} of
     * the {@code BoundedContext}.
     */
    Stand stand() {
        return checkNotNull(stand);
    }

    /**
     * Sets a custom {@link Enricher} for events posted to
     * the {@code EventBus} of the context being built.
     *
     * <p>If the {@code Enricher} is not set, the enrichments
     * will <strong>NOT</strong> be supported in this context.
     *
     * @param enricher
     *         the {@code Enricher} for events or {@code null} if enrichment is not supported
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder enrichEventsUsing(EventEnricher enricher) {
        eventBus.injectEnricher(enricher);
        return this;
    }

    /**
     * Convenience method for handling the cases of passing a repository, which is also a message
     * dispatcher to {@code addXxxDispatcher()} and {@code removeXxxDispatcher()} methods.
     *
     * <p>Such a call can be made by a developer (presumably by mistake) because some repositories
     * <em>are</em> command- or event-dispatchers. Even though the methods
     * {@link #add(Repository)} and {@link #remove(Repository)} are the correct way of handling
     * removing repositories, we do not want to play hard in this case, and simply divert the flow
     * to process the passed {@code Repository} via correct counterpart methods.
     *
     * @param dispatcher
     *         the instance to handle
     * @param repositoryConsumer
     *         the operation to perform if the passed instance is a {@code Repository}
     * @param dispatcherConsumer
     *         the operation to perform if the passed instance is not a {@code Repository}
     * @param <D>
     *         the type of the dispatchers
     * @return this builder
     */
    @CanIgnoreReturnValue
    private <D extends MessageDispatcher<?, ?>>
    BoundedContextBuilder ifRepository(D dispatcher,
                                       Consumer<Repository<?, ?>> repositoryConsumer,
                                       Consumer<D> dispatcherConsumer) {
        if (dispatcher instanceof Repository) {
            repositoryConsumer.accept((Repository<?, ?>) dispatcher);
        } else {
            dispatcherConsumer.accept(dispatcher);
        }
        return this;
    }

    /**
     * Adds the given assignee to the Bounded Context.
     *
     * @param assignee
     *         the assignee to add
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addAssignee(AbstractAssignee assignee) {
        return addCommandDispatcher(assignee);
    }

    /**
     * Adds the passed command dispatcher to the dispatcher registration list that will be
     * processed after the Bounded Context is created.
     *
     * @apiNote This method is also capable of registering user-defined
     *         {@linkplain Repository repositories}
     *         that implement {@code CommandDispatcher}, but the {@link #add(Repository)} method
     *         should be preferred for this purpose. The repositories provided by the framework
     *         do not implement {@code CommandDispatcher}: those that dispatch commands do so
     *         via {@link io.spine.server.commandbus.CommandDispatcherDelegate
     *         CommandDispatcherDelegate}.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addCommandDispatcher(CommandDispatcher commandDispatcher) {
        checkNotNull(commandDispatcher);
        return ifRepository(commandDispatcher, this::add, commandDispatchers::add);
    }

    /**
     * Adds a filter for commands.
     *
     * <p>The order of appending the filters to the builder is the order of the filters in
     * the {@code CommandBus}.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addCommandFilter(BusFilter<CommandEnvelope> filter) {
        checkNotNull(filter);
        commandBus.appendFilter(filter);
        return this;
    }

    /**
     * Adds a listener for commands posted to the {@code CommandBus} of the context being built.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addCommandListener(Listener<CommandEnvelope> listener) {
        checkNotNull(listener);
        commandBus.addListener(listener);
        return this;
    }

    /**
     * Adds the passed event dispatcher to the dispatcher registration list that will be processed
     * after the Bounded Context is created.
     *
     * @apiNote This method is also capable of registering
     *         {@linkplain Repository repositories}
     *         that implement {@code EventDispatcher}, but the {@link #add(Repository)} method
     *         should be preferred for this purpose.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addEventDispatcher(EventDispatcher eventDispatcher) {
        checkNotNull(eventDispatcher);
        return ifRepository(eventDispatcher, this::add, eventDispatchers::add);
    }

    /**
     * Adds a filter for events.
     *
     * <p>The order of appending the filters to the builder is the order of the filters in
     * the {@code EventBus}.
     *
     * @param filter
     *         the filter to add
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addEventFilter(BusFilter<EventEnvelope> filter) {
        checkNotNull(filter);
        eventBus.appendFilter(filter);
        return this;
    }

    /**
     * Adds a listener of the events posted to the {@code EventBus} of the context being built.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder addEventListener(Listener<EventEnvelope> listener) {
        checkNotNull(listener);
        eventBus.addListener(listener);
        return this;
    }

    /**
     * Adds the {@linkplain DefaultRepository default repository} for the passed entity class to
     * the repository registration list.
     */
    @CanIgnoreReturnValue
    public <I, E extends Entity<I, ?>> BoundedContextBuilder add(Class<E> entityClass) {
        checkNotNull(entityClass);
        return add(DefaultRepository.of(entityClass));
    }

    /**
     * Adds the passed repository to the registration list, which will be processed after
     * the Bounded Context is created.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder add(Repository<?, ?> repository) {
        checkNotNull(repository);
        repositories.add(repository);
        return this;
    }

    /**
     * Removes the passed command dispatcher from the corresponding registration list.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder removeCommandDispatcher(CommandDispatcher commandDispatcher) {
        checkNotNull(commandDispatcher);
        return ifRepository(commandDispatcher, this::remove, commandDispatchers::remove);
    }

    /**
     * Removes the passed event dispatcher from the corresponding registration list.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder removeEventDispatcher(EventDispatcher eventDispatcher) {
        checkNotNull(eventDispatcher);
        return ifRepository(eventDispatcher, this::remove, eventDispatchers::remove);
    }

    /**
     * Removes the repository from the registration list by the passed entity class.
     */
    @CanIgnoreReturnValue
    public <I, E extends Entity<I, ?>> BoundedContextBuilder remove(Class<E> entityClass) {
        checkNotNull(entityClass);
        repositories.removeIf(repository -> repository.entityClass()
                                                      .equals(entityClass));
        return this;
    }

    /**
     * Removes the passed repository from the registration list.
     */
    @CanIgnoreReturnValue
    public BoundedContextBuilder remove(Repository<?, ?> repository) {
        checkNotNull(repository);
        repositories.remove(repository);
        return this;
    }

    /**
     * Verifies if the passed command dispatcher was previously added into the registration list
     * of the Bounded Context this builder is going to build.
     */
    @VisibleForTesting
    boolean hasCommandDispatcher(CommandDispatcher commandDispatcher) {
        checkNotNull(commandDispatcher);
        if (commandDispatcher instanceof Repository) {
            return hasRepository((Repository<?, ?>) commandDispatcher);
        }
        var result = commandDispatchers.contains(commandDispatcher);
        return result;
    }

    /**
     * Verifies if the passed event dispatcher was previously added into the registration list
     * of the Bounded Context this builder is going to build.
     */
    @VisibleForTesting
    boolean hasEventDispatcher(EventDispatcher eventDispatcher) {
        checkNotNull(eventDispatcher);
        if (eventDispatcher instanceof Repository) {
            return hasRepository((Repository<?, ?>) eventDispatcher);
        }
        var result = eventDispatchers.contains(eventDispatcher);
        return result;
    }

    /**
     * Verifies if the repository with a passed entity class was previously added into the
     * registration list of the Bounded Context this builder is going to build.
     */
    @VisibleForTesting
    <I, E extends Entity<I, ?>> boolean hasRepository(Class<E> entityClass) {
        checkNotNull(entityClass);
        var result =
                repositories.stream()
                        .anyMatch(repository -> repository.entityClass()
                                                          .equals(entityClass));
        return result;
    }

    /**
     * Verifies if the passed repository was previously added into the registration list
     * of the Bounded Context this builder is going to build.
     */
    @VisibleForTesting
    boolean hasRepository(Repository<?, ?> repository) {
        checkNotNull(repository);
        var result = repositories.contains(repository);
        return result;
    }

    /**
     * Obtains the list of repositories added to the builder by the time of the call.
     *
     * <p>Adding repositories to the builder after this method returns will not update the
     * returned list.
     */
    public ImmutableList<Repository<?, ?>> repositories() {
        return ImmutableList.copyOf(repositories);
    }

    /**
     * Obtains the types of the commands that the context built by this builder is going
     * to handle.
     *
     * <p>The commands are those of the repositories and the command dispatchers added to
     * the builder by the time of the call.
     */
    ImmutableSet<TypeUrl> commandTypes() {
        var ofRepositories = repositories.stream()
                .filter(CommandDispatcherDelegate.class::isInstance)
                .map(CommandDispatcherDelegate.class::cast)
                .flatMap(repository -> repository.commandClasses().stream());
        var ofDispatchers = commandDispatchers.stream()
                .flatMap(dispatcher -> dispatcher.messageClasses().stream());
        return Stream.concat(ofRepositories, ofDispatchers)
                .map(MessageClass::typeUrl)
                .collect(toImmutableSet());
    }

    /**
     * Obtains the {@link AggregateRootDirectory} to be used in the built context.
     *
     * <p>If no custom implementation is specified, an in-mem implementation is used.
     */
    @SuppressWarnings("deprecation") // The `AggregateRootDirectory` API is deprecated.
    AggregateRootDirectory aggregateRootDirectory() {
        if (rootDirectory == null) {
            rootDirectory = InMemoryRootDirectory::new;
        }
        return rootDirectory.get();
    }

    /**
     * Sets the supplier of {@link AggregateRootDirectory}-s to use in the built context.
     *
     * <p>By default, an in-mem implementation is used.
     *
     * @param directory
     *         the supplier of aggregate root directories
     * @deprecated This API does not provide isolation for an invariant.
     *         To coordinate the work of several
     *         {@link io.spine.server.aggregate.Aggregate Aggregate}s, please use
     *         a {@link io.spine.server.procman.ProcessManager ProcessManager} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    @SuppressWarnings("DeprecatedIsStillUsed" /* `AggregateRootDirectory` */)
    public BoundedContextBuilder
    setAggregateRootDirectory(Supplier<AggregateRootDirectory> directory) {
        this.rootDirectory = checkNotNull(directory);
        return this;
    }

    /**
     * Obtains the configuration of the System context.
     *
     * <p>With it, users are able to change the behavior of the system context.
     *
     * @see SystemSettings
     */
    public SystemSettings systemSettings() {
        return systemSettings;
    }

    /**
     * Sets the factory with which the repositories of the built context create their storages.
     *
     * <p>If not set, the factory {@linkplain ServerEnvironment#storageFactory() configured} in
     * the server environment of the application is used.
     *
     * <p>The System context of the built context uses the same factory.
     *
     * @param factory
     *         the factory of the storages
     */
    @CanIgnoreReturnValue
    BoundedContextBuilder setStorageFactory(StorageFactory factory) {
        this.storageFactory = checkNotNull(factory);
        return this;
    }

    /**
     * Obtains the factory with which the repositories of the built context create
     * their storages.
     *
     * @return the factory, or {@code null} if it was not
     *         {@linkplain #setStorageFactory(StorageFactory) set} and the context
     *         was not built yet
     */
    @Nullable StorageFactory getStorageFactory() {
        return storageFactory;
    }

    /**
     * Sets the delivery in which the repositories of the built context register their inboxes.
     *
     * <p>If not set, the delivery {@linkplain ServerEnvironment#delivery() of} the server
     * environment is used.
     *
     * <p>The System context of the built context uses the same delivery.
     *
     * @param delivery
     *         the delivery of the messages to the entities of the context
     */
    @CanIgnoreReturnValue
    BoundedContextBuilder setDelivery(Delivery delivery) {
        this.delivery = checkNotNull(delivery);
        return this;
    }

    /**
     * Obtains the delivery in which the repositories of the built context register
     * their inboxes.
     *
     * @return the delivery, or {@code null} if it was not
     *         {@linkplain #setDelivery(Delivery) set} and the context was not built yet
     */
    @Nullable Delivery getDelivery() {
        return delivery;
    }

    /**
     * Sets the factory of the scheduler of the commands posted to the built context.
     *
     * <p>A scheduler serves the {@code CommandBus} of a single context, and is closed with it.
     * So, the built context and its System context each obtain their own scheduler from
     * the factory.
     *
     * <p>If not set, the contexts obtain their schedulers
     * {@linkplain ServerEnvironment#newCommandScheduler() from} the server environment
     * of the application.
     *
     * @param factory
     *         creates a new scheduler on each call
     */
    @CanIgnoreReturnValue
    BoundedContextBuilder setCommandSchedulerFactory(Supplier<CommandScheduler> factory) {
        this.commandSchedulerFactory = checkNotNull(factory);
        return this;
    }

    /**
     * Obtains the factory of the scheduler of the commands posted to the built context.
     *
     * @return the factory, or {@code null} if it was not
     *         {@linkplain #setCommandSchedulerFactory(Supplier) set} and the context
     *         was not built yet
     */
    @Nullable Supplier<CommandScheduler> getCommandSchedulerFactory() {
        return commandSchedulerFactory;
    }

    /**
     * Sets the transport through which the built context exchanges messages with
     * other contexts.
     *
     * <p>If not set, the factory {@linkplain ServerEnvironment#transportFactory() configured} in
     * the server environment of the application is used.
     *
     * <p>The System context of the built context uses the same factory.
     *
     * @param factory
     *         the factory of the transport
     */
    @CanIgnoreReturnValue
    BoundedContextBuilder setTransportFactory(TransportFactory factory) {
        this.transportFactory = checkNotNull(factory);
        return this;
    }

    /**
     * Obtains the transport through which the built context exchanges messages with
     * other contexts.
     *
     * @return the factory, or {@code null} if it was not
     *         {@linkplain #setTransportFactory(TransportFactory) set} and the context
     *         was not built yet
     */
    @Nullable TransportFactory getTransportFactory() {
        return transportFactory;
    }

    /**
     * Sets the factory of the tracers for the messages of the built context.
     *
     * <p>If not set, the factory {@linkplain ServerEnvironment#tracing() configured} in
     * the server environment of the application is used, if any.
     *
     * <p>The System context of the built context, which traces the messages, uses
     * this factory.
     *
     * @param factory
     *         the factory of the tracers
     */
    @CanIgnoreReturnValue
    BoundedContextBuilder setTracerFactory(TracerFactory factory) {
        this.tracerFactory = checkNotNull(factory);
        return this;
    }

    /**
     * Obtains the factory of the tracers for the messages of the built context.
     *
     * @return the factory, or {@code null} if it was not
     *         {@linkplain #setTracerFactory(TracerFactory) set} and the context was not
     *         built yet, or if the server environment of the application
     *         has no tracing configured
     */
    @Internal
    public @Nullable TracerFactory getTracerFactory() {
        return tracerFactory;
    }

    /**
     * Builds a test-only Bounded Context.
     *
     * <p>Only a builder created by one of the {@code assumingTests()} methods, such as
     * {@link #assumingTests()}, can build its context by itself. The context is the only
     * context of a new {@link Application}. The application has a new {@link ServerEnvironment}
     * with the technologies of the current environment type: in tests, the in-memory storage
     * and transport, and a local {@code Delivery}. The technologies set on this builder are
     * used instead of those of the environment.
     *
     * <p>Closing the context closes the application as well, once the context and its System
     * context are closed. The application then closes its tenant index, if it created one, and
     * the environment. If the application fails to build, the environment is closed at once.
     *
     * <p>To build several contexts that work together, or to give a context the technologies
     * of a particular environment, add the builders to an {@code Application} instead.
     *
     * @return new {@code BoundedContext}
     * @throws IllegalStateException
     *         if this builder was not created for tests
     */
    @Internal
    @VisibleForTesting
    @SuppressWarnings("ProhibitedExceptionThrown" /* Rethrowing what was caught. */)
    public BoundedContext build() {
        checkState(forTests,
                   "The context `%s` cannot be built by itself, because its builder was not" +
                           " created for tests. Add the builder to an `Application` instead.",
                   name().getValue());
        var environment = ServerEnvironment.newBuilder()
                .build();
        Application application;
        try {
            application = Application.newBuilder()
                    .setServerEnvironment(environment)
                    .add(this)
                    .build();
        } catch (Throwable t) {
            closeAfterFailure(environment, t);
            throw t;
        }
        // An application builds only domain contexts.
        var context = (DomainContext) application.contexts()
                                                 .get(0);
        context.runAfterClose(application::close);
        return context;
    }

    /**
     * Creates a new instance of {@code BoundedContext} with the set configurations.
     *
     * <p>The {@link Application} calls this method when it is built. The technologies not set
     * on this builder are taken from the {@link ServerEnvironment} of the application.
     *
     * <p>The resulting domain-specific Bounded Context has as internal System Bounded Context.
     * The entities of the System domain describe the entities of the resulting Bounded Context.
     *
     * <p>The System Bounded Context shares some configuration with the Domain Bounded Context,
     * such as:
     * <ul>
     *     <li>tenancy;
     *     <li>the {@linkplain #setStorageFactory(StorageFactory) storage factory};
     *     <li>the {@linkplain #setDelivery(Delivery) delivery};
     *     <li>the {@linkplain #setCommandSchedulerFactory(Supplier) factory of command schedulers},
     *         from which each context obtains its own scheduler;
     *     <li>the {@linkplain #setTransportFactory(TransportFactory) transport factory};
     *     <li>the {@linkplain #setTracerFactory(TracerFactory) tracer factory}.
     * </ul>
     *
     * <p>All the other configuration is NOT shared.
     *
     * <p>The name of the System Bounded Context is derived from the name of the Domain Bounded
     * Context.
     *
     * <p>If registering the repositories or the dispatchers fails, the method closes
     * the context before throwing.
     *
     * @param environment
     *         the environment of the application of the context
     * @return new {@code BoundedContext}
     */
    @SuppressWarnings("ProhibitedExceptionThrown" /* Rethrowing what was caught. */)
    BoundedContext build(ServerEnvironment environment) {
        useTechnologiesOf(environment);
        var system = buildSystem();
        var result = buildDomain(system);
        try {
            logger().atDebug()
                    .log(() -> format("%s created.", result.nameForLogging()));
            registerRepositories(result);
            registerDispatchers(result);
        } catch (Throwable t) {
            closeAfterFailure(result, t);
            throw t;
        }
        return result;
    }

    /**
     * Closes what was created for a build that failed.
     *
     * <p>The caller never gets it, so nobody else can close it. Left open, a context would
     * keep its repositories, and their inboxes in the {@code Delivery}; an environment,
     * its storage and its transport.
     *
     * @param resource
     *         the context or the environment to close
     * @param failure
     *         the error that failed the build; whatever closing throws, even an
     *         {@code Error}, is added to it as suppressed, so it does not replace the failure,
     *         unless closing throws the failure itself
     */
    private static void closeAfterFailure(Closeable resource, Throwable failure) {
        // The given failure comes back as is, with the failure of closing added to it.
        runKeepingFailure(failure, resource::close);
    }

    EventBus buildEventBus(BoundedContext context) {
        checkNotNull(context);
        eventBus.injectContext(context);
        eventBus.addListener(stand.eventListener());
        return eventBus.build();
    }

    /**
     * Sets the action to be performed before the context is closed.
     *
     * <p>The action is performed after the context is closed, but before the
     * {@linkplain BoundedContext#close() context's own action} is performed.
     *
     * @param consumer
     *         the action to perform
     */
    public BoundedContextBuilder setOnBeforeClose(Consumer<BoundedContext> consumer) {
        this.onBeforeClose = checkNotNull(consumer);
        return this;
    }

    @Nullable Consumer<BoundedContext> getOnBeforeClose() {
        return onBeforeClose;
    }

    CommandBus buildCommandBus() {
        return commandBus.build();
    }

    private void registerRepositories(BoundedContext result) {
        for (var repository : repositories) {
            result.register(repository);
            logger().atDebug()
                    .log(() -> format("`%s` registered.", repository));
        }
    }

    private void registerDispatchers(BoundedContext result) {
        commandDispatchers.forEach(result::registerCommandDispatcher);
        eventDispatchers.forEach(result::registerEventDispatcher);
    }

    private BoundedContext buildDomain(SystemContext system) {
        var systemClient = system.createClient();
        Function<BoundedContextBuilder, DomainContext> instanceFactory =
                builder -> DomainContext.newInstance(builder, systemClient);
        BoundedContext result = doBuild(instanceFactory, systemClient, system.stand());
        system.attachDomain(result);
        return result;
    }

    private SystemContext buildSystem() {
        var system = new BoundedContextBuilder(systemSpec(), systemSettings,
                                               /* forTests = */ false);
        shareTechnologiesWith(system);
        var result = system.doBuild(SystemContext::newInstance, NoOpSystemClient.INSTANCE);
        return result;
    }

    /**
     * Passes the technologies set on this builder to the builder of the System context.
     */
    private void shareTechnologiesWith(BoundedContextBuilder system) {
        system.storageFactory = storageFactory;
        system.delivery = delivery;
        system.commandSchedulerFactory = commandSchedulerFactory;
        system.transportFactory = transportFactory;
        system.tracerFactory = tracerFactory;
    }

    /**
     * Takes the technologies not set on this builder from the given environment.
     */
    private void useTechnologiesOf(ServerEnvironment environment) {
        checkNotNull(environment);
        if (storageFactory == null) {
            storageFactory = environment.storageFactory();
        }
        if (delivery == null) {
            delivery = environment.delivery();
        }
        if (commandSchedulerFactory == null) {
            commandSchedulerFactory = environment::newCommandScheduler;
        }
        if (transportFactory == null) {
            transportFactory = environment.transportFactory();
        }
        if (tracerFactory == null) {
            tracerFactory = environment.tracing()
                                       .orElse(null);
        }
    }

    private ContextSpec systemSpec() {
        var systemSpec = this.spec.toSystem();
        if (!systemSettings.includePersistentEvents()) {
            systemSpec = systemSpec.notStoringEvents();
        }
        return systemSpec;
    }

    private <B extends BoundedContext>
    B doBuild(Function<BoundedContextBuilder, B> instanceFactory, SystemClient client) {
        return doBuild(instanceFactory, client, null);
    }

    private <B extends BoundedContext>
    B doBuild(Function<BoundedContextBuilder, B> instanceFactory,
              SystemClient client,
              @Nullable Stand systemStand) {
        initCommandBus(client.writeSide());
        this.stand = createStand(systemStand);
        var result = instanceFactory.apply(this);
        return result;
    }

    private void initCommandBus(SystemWriteSide systemWriteSide) {
        commandBus.setMultiTenant(isMultiTenant());
        commandBus.injectSystem(systemWriteSide);
    }

    private Stand createStand(@Nullable Stand systemStand) {
        var result = Stand.newBuilder()
                .setMultiTenant(isMultiTenant());
        if (systemStand != null) {
            result.withSubscriptionRegistryFrom(systemStand);
        }
        return result.build();
    }
}
