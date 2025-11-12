/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.core.BoundedContextName;
import io.spine.core.BoundedContextNames;
import io.spine.logging.WithLogging;
import io.spine.option.EntityOption.Visibility;
import io.spine.server.aggregate.AggregateRootDirectory;
import io.spine.server.aggregate.ImportBus;
import io.spine.server.bus.Listener;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.commandbus.CommandDispatcherDelegate;
import io.spine.server.commandbus.DelegatingCommandDispatcher;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Repository;
import io.spine.server.entity.model.EntityClass;
import io.spine.server.event.DelegatingEventDispatcher;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventDispatcher;
import io.spine.server.event.EventDispatcherDelegate;
import io.spine.server.integration.IntegrationBroker;
import io.spine.server.stand.Stand;
import io.spine.server.tenant.TenantIndex;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.SystemClient;
import io.spine.system.server.SystemContext;
import io.spine.type.TypeName;
import org.jspecify.annotations.Nullable;

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
 * belong to the Bounded Context which the entity {@link Repository} is
 * {@linkplain BoundedContext#register(Repository) registered} in.
 *
 * <p>Structurally, a Bounded Context brings together all the infrastructure required for
 * the components of a model to cooperate.
 *
 * <p>An instance of {@code BoundedContext} acts as a major point of configuration for all
 * the model elements which belong to it.
 *
 * @see <a href="https://martinfowler.com/bliki/BoundedContext.html">
 *         Martin Fowler on Bounded Contexts</a>
 */
@SuppressWarnings({"ClassWithTooManyMethods", "OverlyCoupledClass", "NotJavadoc"})
public abstract class BoundedContext
        implements Comparable<BoundedContext>,
                   Closeable,
                   WithLogging {

    /** Basic features of the context. */
    private final ContextSpec spec;

    /** The Command Bus of this context. */
    private final CommandBus commandBus;

    /** The Event Bus of this context. */
    private final EventBus eventBus;

    /** The bus for importing events. */
    private final ImportBus importBus;

    /** The broker for interaction with other contexts. */
    private final IntegrationBroker broker;

    /** The bridge to the read-side of the context. */
    private final Stand stand;

    /** Controls access to entities of all registered repositories. */
    private final VisibilityGuard guard = VisibilityGuard.newInstance();

    /** Provides access to data parts of aggregate roots of this context. */
    private final AggregateRootDirectory aggregateRootDirectory;

    /** The index of tenants having data in this context. */
    private final TenantIndex tenantIndex;

    /** Provides access to internally used features of the context. */
    private final InternalAccess internalAccess;

    private final @Nullable Consumer<BoundedContext> onBeforeClose;

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
        this.eventBus = builder.buildEventBus(this);
        this.stand = builder.stand();
        this.tenantIndex = builder.buildTenantIndex();
        this.broker = new IntegrationBroker();
        this.commandBus = builder.buildCommandBus();
        this.importBus = buildImportBus(tenantIndex);
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
        broker.registerWith(this);
        commandBus.initObservers(eventBus);
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

    private static ImportBus buildImportBus(TenantIndex tenantIndex) {
        var result = ImportBus.newBuilder()
                .injectTenantIndex(tenantIndex);
        return result.build();
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
     */
    protected final void register(Repository<?, ?> repository) {
        checkNotNull(repository);
        registerIfAware(repository);
        guard.register(repository);
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
        if (dispatcher instanceof EventDispatcherDelegate) {
            var eventDispatcher = (EventDispatcherDelegate) dispatcher;
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
        if (dispatcher instanceof CommandDispatcherDelegate) {
            var commandDispatcher = (CommandDispatcherDelegate) dispatcher;
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
        if (dispatcher instanceof CommandDispatcherDelegate) {
            var commandDispatcher = (CommandDispatcherDelegate) dispatcher;
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
        if (contextPart instanceof ContextAware) {
            var contextAware = (ContextAware) contextPart;
            if (!contextAware.isRegistered()) {
                contextAware.registerWith(this);
            }
        }
    }

    /**
     * If the given {@code contextPart} is {@link ContextAware},
     * {@linkplain ContextAware#unregister() unregisters} it from this context.
     */
    private static void unregisterIfAware(Object contextPart) {
        if (contextPart instanceof ContextAware) {
            var contextAware = (ContextAware) contextPart;
            if (contextAware.isRegistered()) {
                contextAware.unregister();
            }
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

    /** Obtains instance of {@link ImportBus} of this {@code BoundedContext}. */
    public ImportBus importBus() {
        return this.importBus;
    }

    /** Obtains instance of {@link Stand} of this {@code BoundedContext}. */
    public Stand stand() {
        return stand;
    }

    /**
     * Obtains an ID of the Bounded Context.
     *
     * <p>The ID allows to identify a Bounded Context if a multi-context application.
     * If the ID was not defined during the building process, the Bounded Context gets
     * {@link BoundedContextNames#assumingTests()} name.
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
     *     <li>Closes {@link ImportBus}.
     *     <li>Closes all registered {@linkplain Repository repositories}.
     *     <li>Removes a {@link Probe}, if it was {@linkplain #install(Probe) installed}.
     * </ol>
     */
    @Override
    public void close() {
        var isOpen = isOpen();
        if (isOpen && onBeforeClose != null) {
            onBeforeClose.accept(this);
        }

        commandBus.closeIfOpen();
        eventBus.closeIfOpen();
        broker.closeIfOpen();
        stand.closeIfOpen();
        importBus.closeIfOpen();

        if (isOpen) {
            shutDownRepositories();
        }
        if (hasProbe()) {
            removeProbe();
        }
        if (isOpen) {
            logger().atDebug().log(() -> format("%s", closed(nameForLogging())));
        }
    }

    @Override
    public boolean isOpen() {
        return commandBus.isOpen();
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
     * Closes all repositories and clears {@link TenantIndex}.
     */
    private void shutDownRepositories() {
        guard.shutDownRepositories();

        if (tenantIndex != null) {
            tenantIndex.close();
        }
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoundedContext)) {
            return false;
        }
        var another = (BoundedContext) o;
        return name().equals(another.name());
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    /**
     * A diagnostic probe which can be {@linkplain #install(Probe) installed} into
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
         * Obtains a tenant index of this Bounded Context.
         *
         * <p>If the context is single-tenant returns
         * {@linkplain TenantIndex#singleTenant() null-object} implementation.
         */
        public TenantIndex tenantIndex() {
            return tenantIndex;
        }

        /**
         * Obtains an {@link AggregateRootDirectory} of this {@code BoundedContext}.
         */
        public AggregateRootDirectory aggregateRootDirectory() {
            return aggregateRootDirectory;
        }

        private BoundedContext self() {
            return BoundedContext.this;
        }
    }
}
