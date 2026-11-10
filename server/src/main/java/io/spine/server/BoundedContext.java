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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.core.BoundedContextName;
import io.spine.logging.WithLogging;
import io.spine.option.EntityOption.Visibility;
import io.spine.server.aggregate.AggregateRootDirectory;
import io.spine.server.bus.Listener;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.commandbus.CommandDispatcherDelegate;
import io.spine.server.commandbus.CommandScheduler;
import io.spine.server.commandbus.DelegatingCommandDispatcher;
import io.spine.server.delivery.Delivery;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Repository;
import io.spine.server.entity.model.EntityClass;
import io.spine.server.event.DelegatingEventDispatcher;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventDispatcher;
import io.spine.server.event.EventDispatcherDelegate;
import io.spine.server.integration.IntegrationBroker;
import io.spine.server.stand.Stand;
import io.spine.server.storage.StorageFactory;
import io.spine.server.transport.TransportFactory;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.SystemClient;
import io.spine.system.server.SystemContext;
import io.spine.type.TypeName;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.server.security.Security.allowOnlyFrameworkServer;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * A logical and structural boundary of a model.
 *
 * <p>Logically, a Bounded Context represents a subsystem built to be described with the same
 * Ubiquitous Language. Any term within a single bounded context has a single meaning and may or
 * may not map to another term in the language of another Bounded Context.
 *
 * <p>The Ubiquitous Language of a Bounded Context is represented by such concepts as the entity
 * state, event, and command types, entity types, and others. An entity and its adjacent types
 * belong to the Bounded Context that the entity {@link Repository} is
 * {@linkplain BoundedContext#register(Repository) registered} in.
 *
 * <p>Structurally, a Bounded Context brings together all the infrastructure required for
 * the components of a model to cooperate.
 *
 * <p>An instance of {@code BoundedContext} acts as a major point of configuration for all
 * the model elements that belong to it.
 *
 * <p>Bounded Contexts are built by an {@link Application}, which gives them the technologies of
 * its {@link ServerEnvironment}. Within an application, a Bounded Context has a name of its own
 * and serves entity state types of its own. {@linkplain Application.Builder#build() Building}
 * the application fails with {@link DuplicateContextNameException} if two of its contexts have
 * the same name, and with {@link DuplicateEntityStateException} if two of its contexts serve
 * one entity state type.
 *
 * @see <a href="https://martinfowler.com/bliki/BoundedContext.html">
 *         Martin Fowler on Bounded Contexts</a>
 */
@SuppressWarnings({"ClassWithTooManyMethods", "NotJavadoc"})
public abstract class BoundedContext
        implements Comparable<BoundedContext>,
                   Closeable,
                   WithLogging {

    /** Basic features of the context. */
    private final ContextSpec spec;

    /** The factory with which the repositories of the context create their storages. */
    private final StorageFactory storageFactory;

    /** The delivery in which the repositories of the context register their inboxes. */
    private final Delivery delivery;

    /** The scheduler of the commands posted to the context. */
    private final CommandScheduler commandScheduler;

    /** The transport through which the context exchanges messages with other contexts. */
    private final TransportFactory transportFactory;

    /** The Command Bus of this context. */
    private final CommandBus commandBus;

    /** The Event Bus of this context. */
    private final EventBus eventBus;

    /** The broker for interaction with other contexts. */
    private final IntegrationBroker broker;

    /** The bridge to the read-side of the context. */
    private final Stand stand;

    /** Controls access to entities of all registered repositories. */
    private final VisibilityGuard guard = VisibilityGuard.newInstance();

    /** Provides access to data parts of aggregate roots of this context. */
    @SuppressWarnings("deprecation") // The `AggregateRootDirectory` API is deprecated.
    private final AggregateRootDirectory aggregateRootDirectory;

    /** Provides access to internally used features of the context. */
    private final InternalAccess internalAccess;

    private final @Nullable Consumer<BoundedContext> onBeforeClose;

    /**
     * The domain context of this System context.
     *
     * <p>Remains {@code null} for a domain context.
     */
    private @MonotonicNonNull BoundedContext domain;

    /**
     * The currently installed probe.
     */
    private @Nullable Probe probe;

    /**
     * Creates new instance.
     *
     * @throws IllegalStateException
     *          if called from a derived class, which is not a part of the framework
     * @apiNote
     * This constructor is for internal use of the framework. Application developers should not
     * create classes derived from {@code BoundedContext}.
     */
    @SuppressWarnings("ThisEscapedInObjectConstruction") // to inject dependencies.
    @Internal
    protected BoundedContext(BoundedContextBuilder builder) {
        super();
        checkInheritance();
        this.spec = builder.spec();
        this.storageFactory = checkNotNull(builder.getStorageFactory());
        this.delivery = checkNotNull(builder.getDelivery());
        this.commandScheduler = checkNotNull(builder.getCommandSchedulerFactory()).get();
        this.transportFactory = checkNotNull(builder.getTransportFactory());
        this.eventBus = builder.buildEventBus(this);
        this.stand = builder.stand();
        this.broker = new IntegrationBroker();
        this.commandBus = builder.buildCommandBus();
        this.aggregateRootDirectory = builder.aggregateRootDirectory();
        this.internalAccess = new InternalAccess();
        this.onBeforeClose = builder.getOnBeforeClose();
    }

    /**
     * Performs post-creation initialization of the instance.
     *
     * <p>This method must be called shortly after the constructor so that the instance can
     * perform dependency injections steps that cannot be performed in the constructor.
     */
    protected final void init() {
        eventBus.registerWith(this);
        commandBus.registerWith(this);
        broker.registerWith(this);
    }

    /**
     * Prevents 3rd party code from creating classes extending from {@code BoundedContext}.
     */
    @SuppressWarnings("ClassReferencesSubclass")
    private void checkInheritance() {
        var thisClass = getClass();
        checkState(
                DomainContext.class.equals(thisClass) ||
                        SystemContext.class.equals(thisClass),
                "The class `BoundedContext` is not designed for " +
                        "inheritance by the framework users."
        );
    }

    /**
     * Creates a new builder for a single tenant {@code BoundedContext}.
     *
     * @param name
     *         the name of the built context
     * @return new builder instance
     */
    public static BoundedContextBuilder singleTenant(String name) {
        checkNotNull(name);
        var spec = ContextSpec.singleTenant(name);
        return new BoundedContextBuilder(spec);
    }

    /**
     * Creates a new builder for a multitenant {@code BoundedContext}.
     *
     * @param name
     *         the name of the built context
     * @return new builder instance
     */
    public static BoundedContextBuilder multitenant(String name) {
        checkNotNull(name);
        var spec = ContextSpec.multitenant(name);
        return new BoundedContextBuilder(spec);
    }

    /**
     * Adds the passed repository to the context.
     *
     * @throws IllegalStateException
     *         if this context is closed
     */
    protected final void register(Repository<?, ?> repository) {
        checkNotNull(repository);
        checkOpen();
        registerIfAware(repository);
        guard.register(repository);
        // The guard holds the repository now, so this context serves its entities
        // even if the callback fails.
        repository.onRegistered();
    }

    /**
     * Creates and registers the {@linkplain DefaultRepository default repository} for the passed
     * class of entities.
     *
     * @param entityClass
     *         the class of entities which will be served by a {@link DefaultRepository}
     * @see #register(Repository)
     */
    final <I, E extends Entity<I, ?>> void register(Class<? extends E> entityClass) {
        checkNotNull(entityClass);
        register(DefaultRepository.of(entityClass));
    }

    /**
     * Registers the passed command dispatcher with the {@code CommandBus}.
     *
     * @see #registerCommandDispatcher(CommandDispatcherDelegate)
     */
    @SuppressWarnings("WeakerAccess") // This class is effectively `sealed`, so this is stricter.
    protected void registerCommandDispatcher(CommandDispatcher dispatcher) {
        checkNotNull(dispatcher);
        registerIfAware(dispatcher);
        if (dispatcher.dispatchesCommands()) {
            commandBus().register(dispatcher);
        }
        if (dispatcher instanceof EventDispatcherDelegate eventDispatcher) {
            registerEventDispatcher(eventDispatcher);
        }
    }

    /**
     * Registering the passed command dispatcher delegate with the {@code CommandBus}.
     *
     * @see #registerCommandDispatcher(CommandDispatcher)
     */
    private void registerCommandDispatcher(CommandDispatcherDelegate dispatcher) {
        checkNotNull(dispatcher);
        if (dispatcher.dispatchesCommands()) {
            registerCommandDispatcher(DelegatingCommandDispatcher.of(dispatcher));
        }
    }

    private void unregisterCommandDispatcher(CommandDispatcherDelegate dispatcher) {
        checkNotNull(dispatcher);
        if (dispatcher.dispatchesCommands()) {
            commandBus().unregister(dispatcher);
        }
    }

    /**
     * Registering the passed event dispatcher with the buses of this context.
     *
     * <p>If the given instance dispatches domestic events, registers it with the {@code EventBus}.
     * If the given instance dispatches external events, registers it with
     * the {@code IntegrationBroker}.
     *
     * @see #registerEventDispatcher(EventDispatcherDelegate)
     * @see #unregisterEventDispatcher(EventDispatcher)
     */
    protected void registerEventDispatcher(EventDispatcher dispatcher) {
        checkNotNull(dispatcher);
        allowOnlyFrameworkServer();
        registerIfAware(dispatcher);
        if (dispatcher.dispatchesEvents()) {
            var eventBus = eventBus();
            eventBus.register(dispatcher);
            var systemReadSide = systemClient().readSide();
            systemReadSide.register(dispatcher);
        }
        if (dispatcher.dispatchesExternalEvents()) {
            broker.register(dispatcher);
        }
        if (dispatcher instanceof CommandDispatcherDelegate commandDispatcher) {
            registerCommandDispatcher(commandDispatcher);
        }
    }

    /**
     * Unregisters the passed event dispatcher from the buses of this context.
     *
     * <p>If the given instance dispatches domestic events, unregisters it from
     * the {@code EventBus}. If the given instance dispatches external events, unregisters it from
     * the {@code IntegrationBroker}.
     *
     * @see #registerEventDispatcher(EventDispatcher)
     */
    private void unregisterEventDispatcher(EventDispatcher dispatcher) {
        checkNotNull(dispatcher);
        allowOnlyFrameworkServer();
        unregisterIfAware(dispatcher);
        if (dispatcher.dispatchesEvents()) {
            var eventBus = eventBus();
            eventBus.unregister(dispatcher);
            var systemReadSide = systemClient().readSide();
            systemReadSide.unregister(dispatcher);
        }
        if (dispatcher.dispatchesExternalEvents()) {
            broker.unregister(dispatcher);
        }
        if (dispatcher instanceof CommandDispatcherDelegate commandDispatcher) {
            unregisterCommandDispatcher(commandDispatcher);
        }
    }

    /**
     * Registers the passed delegate of an {@link EventDispatcher} with the buses of this context.
     *
     * @see #registerEventDispatcher(EventDispatcher)
     */
    private void registerEventDispatcher(EventDispatcherDelegate dispatcher) {
        checkNotNull(dispatcher);
        var delegate = DelegatingEventDispatcher.of(dispatcher);
        registerEventDispatcher(delegate);
    }

    /**
     * If the given {@code contextPart} is {@link ContextAware},
     * {@linkplain ContextAware#registerWith registers} it with this context.
     */
    private void registerIfAware(Object contextPart) {
        if (contextPart instanceof ContextAware contextAware && !contextAware.isRegistered()) {
            contextAware.registerWith(this);
        }
    }

    /**
     * If the given {@code contextPart} is {@link ContextAware},
     * {@linkplain ContextAware#unregister() unregisters} it from this context.
     */
    private static void unregisterIfAware(Object contextPart) {
        if (contextPart instanceof ContextAware contextAware && contextAware.isRegistered()) {
            contextAware.unregister();
        }
    }

    /**
     * Obtains a set of entity type names by their visibility.
     */
    public Set<TypeName> stateTypes(Visibility visibility) {
        var result = guard.entityStateTypes(visibility);
        return result;
    }

    /**
     * Obtains the set of all entity type names.
     */
    public Set<TypeName> stateTypes() {
        var result = guard.allEntityTypes();
        return result;
    }

    /**
     * Verifies if this Bounded Context contains entities of the passed class.
     *
     * <p>This method does not take into account the visibility of entity states.
     */
    public boolean hasEntitiesOfType(Class<? extends Entity<?, ?>> entityClass) {
        var cls = EntityClass.asEntityClass(entityClass);
        var result = guard.hasRepository(cls.stateClass());
        return result;
    }

    /**
     * Verifies if this Bounded Context has entities with the state of the passed class.
     *
     * <p>This method does not take into account the visibility of entity states.
     */
    public boolean hasEntitiesWithState(Class<? extends EntityState<?>> stateClass) {
        var result = guard.hasRepository(stateClass);
        return result;
    }

    /** Obtains instance of {@link CommandBus} of this {@code BoundedContext}. */
    public CommandBus commandBus() {
        return this.commandBus;
    }

    /** Obtains instance of {@link EventBus} of this {@code BoundedContext}. */
    public EventBus eventBus() {
        return eventBus;
    }

    /** Obtains instance of {@link Stand} of this {@code BoundedContext}. */
    public Stand stand() {
        return stand;
    }

    /**
     * Obtains an ID of the Bounded Context.
     *
     * <p>The ID allows identifying a Bounded Context in a multi-context application.
     * Two Bounded Contexts of an application cannot have the same ID.
     *
     * @return the ID of this {@code BoundedContext}
     */
    public BoundedContextName name() {
        return spec.name();
    }

    /**
     * Obtains specification of this context.
     */
    public ContextSpec spec() {
        return spec;
    }

    /**
     * Returns {@code true} if the Bounded Context is designed to serve more than one tenant of
     * the application, {@code false} otherwise.
     */
    public boolean isMultitenant() {
        return spec.isMultitenant();
    }

    /**
     * Obtains instance of {@link SystemClient} of this {@code BoundedContext}.
     */
    @Internal
    public abstract SystemClient systemClient();

    /**
     * Tells if this is a System context, which keeps the meta information
     * about the entities of another Bounded Context.
     */
    protected abstract boolean isSystem();

    /**
     * Closes the {@code BoundedContext} performing all necessary clean-ups.
     *
     * <p>This method performs the following:
     * <ol>
     *     <li>Invokes {@link BoundedContextBuilder#getOnBeforeClose onBeforeClose} if it was
     *      configured when the context was {@linkplain BoundedContextBuilder built}.
     *     <li>Closes {@link CommandBus}.
     *     <li>Closes {@link EventBus}.
     *     <li>Closes {@link IntegrationBroker}.
     *     <li>Closes {@link Stand}.
     *     <li>Closes all registered {@linkplain Repository repositories}.
     *     <li>Removes a {@link Probe}, if it was {@linkplain #install(Probe) installed}.
     * </ol>
     *
     * <p>If {@code onBeforeClose} throws, the method still closes the rest, and then throws.
     * If closing one of the parts listed above throws, the method still closes the others,
     * and then throws.
     */
    @Override
    public void close() {
        var isOpen = isOpen();
        try {
            if (isOpen && onBeforeClose != null) {
                onBeforeClose.accept(this);
            }
        } finally {
            closeComponents(isOpen);
        }
    }

    /**
     * Closes the buses, the repositories, and the probe of this context.
     *
     * <p>Closes each of them even if closing another one fails, and then throws the first
     * failure, with the later ones suppressed. A context that failed to close is no longer
     * open, so it cannot be closed again to retry.
     *
     * <p>Shuts down the repositories last. Until then, the context keeps its name and its
     * entity state types, so another context cannot take them while the buses of this one
     * may still be open.
     *
     * @param wasOpen
     *         tells if the context was open when the closing started
     */
    private void closeComponents(boolean wasOpen) {
        RuntimeException failure = null;
        for (var component : List.of(commandBus, eventBus, broker, stand)) {
            try {
                component.closeIfOpen();
            } catch (RuntimeException e) {
                failure = withSuppressed(failure, e);
            }
        }
        if (wasOpen) {
            try {
                shutDownRepositories();
            } catch (RuntimeException e) {
                failure = withSuppressed(failure, e);
            }
        }
        if (hasProbe()) {
            removeProbe();
        }
        if (wasOpen) {
            logger().atDebug().log(() -> format("%s", closed(nameForLogging())));
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Returns the first failure, adding the passed one to it as suppressed,
     * or the passed failure if it is the first one.
     *
     * <p>If the passed failure is the first one itself, it is not added: an exception
     * cannot suppress itself.
     */
    private static RuntimeException
    withSuppressed(@Nullable RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    @Override
    public boolean isOpen() {
        return commandBus.isOpen();
    }

    /**
     * Tells this System context which domain context it serves.
     *
     * <p>The builder of the domain context calls this method once the domain context is built.
     *
     * @throws IllegalStateException
     *         if this is a domain context, or if the domain context is already set
     */
    final void attachDomain(BoundedContext domain) {
        checkNotNull(domain);
        checkState(isSystem(), "The context `%s` is not a System context.", this);
        checkState(this.domain == null,
                   "The System context `%s` already serves a domain context.", this);
        this.domain = domain;
    }

    final String nameForLogging() {
        return BoundedContext.class.getSimpleName() + ' ' + name().getValue();
    }

    /**
     * Returns the passed name with added suffix {@code " closed."}.
     */
    private static String closed(String name) {
        return name + " closed.";
    }

    /**
     * Closes all repositories.
     */
    private void shutDownRepositories() {
        guard.shutDownRepositories();
    }

    /**
     * Returns the name of this Bounded Context.
     */
    @Override
    public String toString() {
        return spec.name().value();
    }

    /**
     * Provides access to features of the context used internally by the framework.
     *
     * @throws SecurityException
     *         if called from outside the framework
     */
    @Internal
    public final InternalAccess internalAccess() {
        allowOnlyFrameworkServer();
        return internalAccess;
    }

    /**
     * Compares two bounded contexts by their names.
     */
    @Override
    public int compareTo(BoundedContext another) {
        checkNotNull(another);
        return name().value().compareTo(another.name().value());
    }

    /**
     * Installs the passed probe into this context.
     *
     * @see #hasProbe()
     * @see #probe()
     * @see #removeProbe()
     * @throws IllegalStateException
     *          if another probe is already installed
     */
    public final void install(Probe probe) {
        checkNotNull(probe);
        checkState(this.probe == null || probe.equals(this.probe),
                   "Probe is already installed (`%s`). Please remove previous probe first.", probe);
        if (probe.equals(this.probe)) {
            return;
        }
        probe.registerWith(this);
        commandBus.add(probe.commandListener());
        eventBus.add(probe.eventListener());
        probe.eventDispatchers()
             .forEach(this::registerEventDispatcher);
        this.probe = probe;
    }

    /**
     * Removes the currently installed probe, unregistering it with this context.
     *
     * @throws IllegalStateException
     *          if no probe was installed before
     */
    public final void removeProbe() {
        checkState(probe != null, "Probe is not installed.");
        commandBus.remove(probe.commandListener());
        eventBus.remove(probe.eventListener());
        probe.eventDispatchers()
             .forEach(this::unregisterEventDispatcher);
        probe.unregister();
        probe = null;
    }

    /**
     * Obtains the currently installed {@link Probe} or {@link Optional#empty()} if no probe
     * is installed.
     */
    public final Optional<Probe> probe() {
        return Optional.ofNullable(probe);
    }

    /**
     * Returns {@code true} if a {@link Probe} is installed, {@code false} otherwise.
     */
    public final boolean hasProbe() {
        return probe != null;
    }

    /**
     * Returns {@code true} if another bounded context has the same name as this one,
     * {@code false} otherwise.
     */
    @Override
    public boolean equals(@Nullable Object o) {
        return (this == o) ||
                ((o instanceof BoundedContext another) && name().equals(another.name()));
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    /**
     * A diagnostic probe that can be {@linkplain #install(Probe) installed} into
     * a Bounded Context to collect information about the commands and events processed by it.
     */
    public interface Probe extends ContextAware {

        /**
         * The listener of commands processed by the context.
         */
        Listener<CommandEnvelope> commandListener();

        /**
         * The listener of events processed by the context.
         */
        Listener<EventEnvelope> eventListener();

        /**
         * Obtains the event dispatchers exposed by the probe for gathering
         * additional information about the events.
         */
        Set<EventDispatcher> eventDispatchers();
    }

    /**
     * Provides access to features of {@link BoundedContext} used internally by the framework.
     */
    @Internal
    public final class InternalAccess {

        /** Prevents instantiation from the outside. */
        private InternalAccess() {
        }

        /**
         * Registers the passed repository.
         *
         * @return this instance of {@code InternalAccess} for call chaining
         * @throws IllegalStateException
         *         if the context is closed
         * @see BoundedContext#register(Repository)
         */
        @CanIgnoreReturnValue
        public InternalAccess register(Repository<?, ?> repository) {
            self().register(checkNotNull(repository));
            return this;
        }

        /**
         * Registers the passed command dispatcher.
         *
         * @return this instance of {@code InternalAccess} for call chaining
         * @see BoundedContext#registerCommandDispatcher(CommandDispatcher)
         */
        @CanIgnoreReturnValue
        public InternalAccess registerCommandDispatcher(CommandDispatcher dispatcher) {
            self().registerCommandDispatcher(checkNotNull(dispatcher));
            return this;
        }

        /**
         * Registers the passed event dispatcher.
         *
         * @return this instance of {@code InternalAccess} for call chaining
         * @see BoundedContext#registerEventDispatcher(EventDispatcher)
         */
        @CanIgnoreReturnValue
        public InternalAccess registerEventDispatcher(EventDispatcher dispatcher) {
            self().registerEventDispatcher(dispatcher);
            return this;
        }

        /**
         * Obtains repositories of the context.
         *
         * @throws IllegalStateException
         *         if there are no repository entities of which have the passed state
         */
        public Repository<?, ?> getRepository(Class<? extends EntityState<?>> stateClass) {
            return guard.get(stateClass);
        }

        /**
         * Finds a repository by the state class of entities.
         *
         * <p>This method assumes that a repository for the given entity state class <b>is</b>
         * registered in this context. If there is no such repository, throws
         * an {@link IllegalStateException}.
         *
         * <p>If a repository is registered, the method returns it or {@link Optional#empty()} if
         * the requested entity is {@linkplain Visibility#NONE not visible}.
         *
         * @param stateCls
         *         the class of the entity state managed by the resulting repository
         * @return the requested repository or {@link Optional#empty()} if the repository manages
         *         a {@linkplain Visibility#NONE non-visible} entity
         * @throws IllegalStateException
         *         if the requested repository is not registered
         * @see VisibilityGuard
         */
        public Optional<Repository<?, ?>> findRepository(Class<? extends EntityState<?>> stateCls) {
            // See if there is a repository for this state at all.
            if (!guard.hasRepository(stateCls)) {
                throw newIllegalStateException(
                        "No repository found for the entity state class `%s`.",
                        stateCls.getName()
                );
            }
            var repository = guard.repositoryFor(stateCls);
            return repository;
        }

        /** Obtains an {@link IntegrationBroker} of this {@code BoundedContext}. */
        public IntegrationBroker broker() {
            return self().broker;
        }

        /**
         * Obtains the storage factory with which the repositories of this
         * {@code BoundedContext} create their storages.
         */
        public StorageFactory storageFactory() {
            return self().storageFactory;
        }

        /**
         * Obtains the domain context: this context, if it is a domain context,
         * or the domain context of this System context.
         *
         * @throws IllegalStateException
         *         if this System context does not know its domain context yet
         */
        public BoundedContext domainContext() {
            var self = self();
            if (!self.isSystem()) {
                return self;
            }
            checkState(self.domain != null,
                       "The System context `%s` does not know its domain context yet.", self);
            return self.domain;
        }

        /**
         * Obtains the {@code Delivery} in which the repositories of this
         * {@code BoundedContext} register their inboxes.
         */
        public Delivery delivery() {
            return self().delivery;
        }

        /**
         * Obtains the scheduler of the commands posted to this {@code BoundedContext}.
         */
        public CommandScheduler commandScheduler() {
            return self().commandScheduler;
        }

        /**
         * Obtains the transport through which the {@code IntegrationBroker} of this
         * {@code BoundedContext} exchanges messages with other contexts.
         */
        public TransportFactory transportFactory() {
            return self().transportFactory;
        }

        /**
         * Obtains an {@link AggregateRootDirectory} of this {@code BoundedContext}.
         */
        @SuppressWarnings("deprecation") // The `AggregateRootDirectory` API is deprecated.
        public AggregateRootDirectory aggregateRootDirectory() {
            return aggregateRootDirectory;
        }

        private BoundedContext self() {
            return BoundedContext.this;
        }
    }
}
