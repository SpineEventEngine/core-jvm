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

package io.spine.testing.server.blackbox;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.extensions.proto.ProtoFluentAssertion;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.CommandMessage;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.client.Client;
import io.spine.client.Query;
import io.spine.client.QueryResponse;
import io.spine.client.Topic;
import io.spine.core.BoundedContextName;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.grpc.MemoizingObserver;
import io.spine.logging.WithLogging;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.Closeable;
import io.spine.server.QueryService;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Repository;
import io.spine.server.event.EventBus;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.CommandSubject;
import io.spine.testing.server.EventSubject;
import io.spine.testing.server.blackbox.probe.BlackboxProbe;
import io.spine.testing.server.blackbox.probe.CommandCollector;
import io.spine.testing.server.blackbox.probe.EventCollector;
import io.spine.testing.server.entity.EntitySubject;
import io.spine.testing.server.query.QueryResultSubject;
import io.spine.time.ZoneId;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.asList;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.server.entity.model.EntityClass.stateClassOf;
import static io.spine.testing.server.blackbox.Actor.defaultActor;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static java.util.Collections.singletonList;
import static java.util.Collections.synchronizedSet;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class provides means for integration testing of Bounded Contexts.
 *
 * <p>Such a test suite would send commands or events to the Bounded Context under the test,
 * and then verify consequences of handling a command or an event.
 */
@SuppressWarnings({"ClassWithTooManyMethods", "OverlyCoupledClass"})
@VisibleForTesting
public abstract class BlackBox implements WithLogging, Closeable {

    /**
     * The context under the test.
     */
    private final BoundedContext context;

    /**
     * The probe inserted into the context under the test.
     */
    private final BlackboxProbe probe;

    /**
     * A factory of {@link Client}s which send requests to this context.
     */
    private final ClientFactory clientFactory;

    /**
     * Information about the current user and the time-zone.
     */
    private Actor actor;

    /**
     * Commands received by this instance and posted to Command Bus during the test setup.
     *
     * <p>These commands are filtered out from those generated in the Bounded Context
     * (in response to posted or generated events or commands), which are used for assertions.
     */
    private final Set<Command> postedCommands;

    /**
     * Events received by this instance and posted to the Event Bus during the test setup.
     *
     * <p>These events are filtered out from those stored in the Bounded Context to
     * collect only the emitted events, which are used for assertions.
     */
    private final Set<Event> postedEvents;

    /**
     * Creates a new instance obtaining configuration parameters from the given context instance.
     */
    public static BlackBox from(BoundedContext context) {
        var box = context.isMultitenant()
                  ? new MtBlackBox(context)
                  : new StBlackBox(context);
        return box;
    }

    /**
     * Creates a new instance obtaining configuration parameters from the passed builder.
     */
    public static BlackBox from(BoundedContextBuilder builder) {
        var context = builder.build();
        return from(context);
    }

    /**
     * Creates a {@code BlackBox} over a single-tenant context with the given components.
     *
     * @see #with(boolean, Object...)
     */
    public static BlackBox singleTenantWith(Object... components) {
        return with(false, components);
    }

    /**
     * Creates a {@code BlackBox} over a single-tenant context with the given name and components.
     *
     * @see #with(boolean, Object...)
     */
    public static BlackBox singleTenant(String name, Object... components) {
        return with(BoundedContext.singleTenant(name), components);
    }

    /**
     * Creates a {@code BlackBox} over a multi-tenant context with the given components.
     *
     * @see #with(boolean, Object...)
     */
    public static BlackBox multiTenantWith(Object... components) {
        checkNotNull(components);
        return with(true, components);
    }

    /**
     * Creates a {@code BlackBox} over a context with the given components.
     *
     * <p>The components can be either {@link Repository} or {@link Entity} classes.
     *
     * <p>For creating a test environment with other components, please use
     * {@link BoundedContextBuilder} and then {@link #from(BoundedContext)}.
     *
     * @param multitenant
     *         whether the context under the test is multitenant
     * @param components
     *         repositories or entity classes to be added to the context under the test
     */
    public static BlackBox with(boolean multitenant, Object... components) {
        checkNotNull(components);
        var builder = BoundedContextBuilder.assumingTests(multitenant);
        return with(builder, components);
    }

    /**
     * Creates a {@code BlackBox} over a context with the given components.
     *
     * <p>The components can be either {@link Repository} or {@link Entity} classes.
     *
     * <p>For creating a test environment with other components, please use
     * {@link BoundedContextBuilder} and then {@link #from(BoundedContext)}.
     *
     * @param builder
     *         the context builder to use for creating the context under the test
     * @param components
     *         repositories or entity classes to be added to the context under the test
     */
    @SuppressWarnings("ChainOfInstanceofChecks") // We allow passing `Object`s to simplify setup.
    public static BlackBox with(BoundedContextBuilder builder, Object... components) {
        checkNotNull(components);
        for (var c : components) {
            checkArgument(c != null, "Null component is not allowed.");
            if (c instanceof Repository) {
                builder.add((Repository<?, ?>) c);
            } else if (c instanceof Class<?>) {
                if (Entity.class.isAssignableFrom((Class<?>) c)) {
                    var entityClass = cast((Class<?>)c);
                    builder.add(entityClass);
                }
            } else {
                throw newIllegalArgumentException(
                        "Unsupported component type: `%s`.", c.getClass().getName()
                );
            }
        }
        return from(builder);
    }

    private static <I, E extends Entity<I, ?>> Class<? extends E> cast(Class<?> cls) {
        @SuppressWarnings("unchecked") // Safe due to the `isAssignableFrom` check.
        var result = (Class<? extends E>) cls;
        return result;
    }

    BlackBox(BoundedContext context) {
        super();
        this.context = context;
        this.probe = new BlackboxProbe();
        context.install(probe);
        this.clientFactory = new ClientFactory(context);
        this.actor = defaultActor();
        this.postedCommands = synchronizedSet(new HashSet<>());
        this.postedEvents = synchronizedSet(new HashSet<>());
    }

    /**
     * Obtains the name of the context under the test.
     */
    public BoundedContextName name() {
        return context.name();
    }

    /**
     * Makes all future requests to the context come to the tenant with the passed ID.
     *
     * @throws IllegalStateException
     *         if the method is called for a single-tenant context
     */
    public abstract BlackBox withTenant(TenantId tenant);

    /**
     * Sets the given {@link UserId} as the actor ID for the requests produced by this context.
     */
    @CanIgnoreReturnValue
    public final BlackBox withActor(UserId user) {
        checkNotNull(user);
        actor = actor.withId(user);
        return this;
    }

    /**
     * Sets the given time zone parameters for the actor requests produced by this context.
     */
    @CanIgnoreReturnValue
    public final BlackBox in(ZoneId zoneId) {
        checkNotNull(zoneId);
        actor = actor.in(zoneId);
        return this;
    }

    /**
     * Tells context to log signal handler failures over failing the test.
     */
    @CanIgnoreReturnValue
    public final BlackBox tolerateFailures() {
        probe.failedHandlerGuard().tolerateFailures();
        return this;
    }

    /**
     * Obtains the current {@link Actor}.
     */
    final Actor actor() {
        return this.actor;
    }

    @VisibleForTesting
    final BoundedContext context() {
        return context;
    }

    /**
     * Appends the passed event to the history of the context under the test.
     */
    @CanIgnoreReturnValue
    public final BlackBox append(Event event) {
        checkNotNull(event);
        var eventStore =
                context.eventBus()
                       .eventStore();
        eventStore.append(event);
        return this;
    }

    /**
     * Sends off a provided command to the Bounded Context.
     *
     * @param domainCommand
     *         a domain command to be dispatched to the Bounded Context
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox receivesCommand(CommandMessage domainCommand) {
        checkNotNull(domainCommand);
        return receivesCommands(singletonList(domainCommand));
    }

    /**
     * Sends off provided commands to the Bounded Context.
     *
     * @param first
     *         a domain command to be dispatched to the Bounded Context first
     * @param second
     *         a domain command to be dispatched to the Bounded Context second
     * @param rest
     *         optional domain commands to be dispatched to the Bounded Context in supplied order
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox
    receivesCommands(CommandMessage first, CommandMessage second, CommandMessage... rest) {
        checkNotNull(first);
        checkNotNull(second);
        return receivesCommands(asList(first, second, rest));
    }

    /**
     * Sends off provided commands to the Bounded Context.
     *
     * @param domainCommands
     *         a list of domain commands to be dispatched to the Bounded Context
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox receivesCommands(List<CommandMessage> domainCommands) {
        checkNotNull(domainCommands);
        var posted = setup().postCommands(domainCommands);
        postedCommands.addAll(posted);
        return this;
    }

    /**
     * Sends off a provided event to the Bounded Context.
     *
     * @param domainEvent
     *         a domain event to be dispatched to the Bounded Context.
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox receivesEvent(EventMessage domainEvent) {
        checkNotNull(domainEvent);
        return receivesEvents(singletonList(domainEvent));
    }

    /**
     * Sends off provided events to the Bounded Context.
     *
     * <p>The method accepts event messages or instances of {@link io.spine.core.Event}.
     * If an instance of {@code Event} is passed, it will be posted to {@link EventBus} as is.
     * Otherwise, an instance of {@code Event} will be generated basing on the passed event
     * message and posted to the bus.
     *
     * @param first
     *         a domain event to be dispatched first
     * @param second
     *         a domain event to be dispatched second
     * @param rest
     *         optional domain events to be dispatched in the supplied order
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox
    receivesEvents(EventMessage first, EventMessage second, EventMessage... rest) {
        checkNotNull(first);
        checkNotNull(second);
        return receivesEvents(asList(first, second, rest));
    }

    /**
     * Sends off a provided event to the Bounded Context as event from an external source.
     *
     * @param messageOrEvent
     *         an event message or {@link Event}. If an instance of {@code Event} is
     *         passed, it will be posted to {@link io.spine.server.integration.IntegrationBroker
     *         IntegrationBroker} as-is.
     *         Otherwise, an instance of {@code Event} will be generated basing
     *         on the passed event message and posted to the bus.
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox receivesExternalEvent(Message messageOrEvent) {
        checkNotNull(messageOrEvent);
        setup().postExternalEvent(messageOrEvent);
        return this;
    }

    /**
     * Sends off provided events to the Bounded Context as events from an external source.
     *
     * <p>The method accepts event messages or instances of {@link io.spine.core.Event}.
     * If an instance of {@code Event} is passed, it will be posted to
     * {@link io.spine.server.integration.IntegrationBroker IntegrationBroker} as-is.
     * Otherwise, an instance of {@code Event} will be generated basing on the passed event
     * message and posted to the bus.
     *
     * @param first
     *         an external event to be dispatched first
     * @param second
     *         an external event to be dispatched second
     * @param other
     *         optional external events to be dispatched in the supplied order
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @SuppressWarnings("unused") // IDEA does not see the usage of this method from tests.
    @CanIgnoreReturnValue
    public final BlackBox
    receivesExternalEvents(EventMessage first, EventMessage second, EventMessage... other) {
        checkNotNull(first);
        checkNotNull(second);
        return receivesExternalEvents(asList(first, second, other));
    }

    /**
     * Sends off provided events to the Bounded Context as events from an external source.
     *
     * @param eventMessages
     *         a list of external events to be dispatched to the Bounded Context
     * @return current instance
     */
    private BlackBox receivesExternalEvents(Collection<EventMessage> eventMessages) {
        setup().postExternalEvents(eventMessages);
        return this;
    }

    /**
     * Sends off events using the specified producer to the Bounded Context.
     *
     * <p>The method is needed to route events based on a proper producer ID.
     *
     * @param producerId
     *         the {@linkplain io.spine.core.EventContext#getProducerId() producer} for events
     * @param first
     *         a domain event to be dispatched to the Bounded Context first
     * @param rest
     *         optional domain events to be dispatched to the Bounded Context in the supplied order
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox
    receivesEventsProducedBy(Object producerId, EventMessage first, EventMessage... rest) {
        var sentEvents = setup().postEvents(producerId, first, rest);
        postedEvents.addAll(sentEvents);
        return this;
    }

    /**
     * Sends off provided events to the Bounded Context.
     *
     * @param domainEvents
     *         a list of domain event to be dispatched to the Bounded Context
     * @return current instance
     */
    private BlackBox receivesEvents(Collection<EventMessage> domainEvents) {
        var sentEvents = setup().postEvents(domainEvents);
        this.postedEvents.addAll(sentEvents);
        return this;
    }

    /**
     * Imports the event into the Bounded Context.
     *
     * @param eventOrMessage
     *         and event message or {@link Event} instance
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public BlackBox importsEvent(Message eventOrMessage) {
        setup().importEvent(eventOrMessage);
        return this;
    }

    /**
     * Imports passed events into the Bounded Context.
     *
     * @param first
     *         an event message or {@code Event} to be imported first
     * @param second
     *         an event message or {@code Event} to be imported second
     * @param rest
     *         optional event messages or instances of {@code Event} to be imported
     *         in the supplied order
     * @return current instance
     * @apiNote Returned value can be ignored when this method invoked for test setup.
     */
    @CanIgnoreReturnValue
    public final BlackBox
    importsEvents(EventMessage first, EventMessage second, EventMessage... rest) {
        return importAll(asList(first, second, rest));
    }

    private BlackBox importAll(Collection<EventMessage> eventMessages) {
        setup().importEvents(eventMessages);
        return this;
    }

    private BlackBoxSetup setup() {
        return new BlackBoxSetup(context, requestFactory(), memoizingObserver());
    }

    /**
     * Closes the {@code BlackBox} performing all necessary clean-ups.
     *
     * <p>This method performs the following:
     * <ol>
     *     <li>Closes the tested {@link BoundedContext}.
     *     <li>Closes the associated {@link io.spine.client.Client Client}s.
     * </ol>
     *
     * <p>Instead of a checked {@link java.io.IOException IOException}, wraps any issues
     * that may occur while closing, into an {@link IllegalStateException}.
     */
    @Override
    public final void close() {
        context.closeIfOpen();
        clientFactory.closeIfOpen();
    }

    @Override
    public boolean isOpen() {
        return context.isOpen() || clientFactory.isOpen();
    }

    /**
     * Obtains the request factory to operate with.
     */
    @SuppressWarnings("TestOnlyProblems")   /* `TestActorRequestFactory` is not test-only. */
    abstract TestActorRequestFactory requestFactory();

    /**
     * Obtains an immutable list of commands generated in this Bounded Context in response
     * to posted messages.
     *
     * <p>The returned list does <em>NOT</em> contain commands posted to this Bounded Context
     * during test setup.
     */
    private ImmutableList<Command> commands() {
        var wasNotReceived =
                ((Predicate<Command>) postedCommands::contains).negate();
        return select(probe.commandListener())
                .stream()
                .filter(wasNotReceived)
                .collect(toImmutableList());
    }

    /**
     * Selects commands that belong to the current tenant.
     */
    abstract ImmutableList<Command> select(CommandCollector collector);

    /**
     * Obtains an immutable list of events generated in this Bounded Context in response
     * to posted messages.
     *
     * <p>The returned list does <em>NOT</em> contain events posted to this Bounded Context
     * during test setup.
     */
    private ImmutableList<Event> events() {
        var wasNotReceived = ((Predicate<Event>) postedEvents::contains).negate();
        return allEvents().stream()
                .filter(wasNotReceived)
                .collect(toImmutableList());
    }

    /**
     * Selects events that belong to the current tenant.
     */
    abstract ImmutableList<Event> select(EventCollector collector);

    /**
     * Obtains an immutable list of all the events in this Bounded Context.
     */
    @VisibleForTesting
    final ImmutableList<Event> allEvents() {
        return select(probe.eventListener());
    }

    /**
     * Performs data reading operation in a tenant context.
     */
    protected <D extends @Nullable Object> D readOperation(Supplier<D> supplier) {
        return supplier.get();
    }

    /**
     * Obtains a Subject for an entity of the passed class with the given ID.
     */
    public final <I, E extends Entity<I, ? extends EntityState<I>>>
    EntitySubject assertEntity(I id, Class<E> entityClass) {
        var found = findEntity(id, entityClass);
        return EntitySubject.assertEntity(found);
    }

    private <I> @Nullable Entity<I, ?>
    findEntity(I id, Class<? extends Entity<I, ?>> entityClass) {
        Class<? extends EntityState<I>> stateClass = stateClassOf(entityClass);
        return findByState(id, stateClass);
    }

    /**
     * Obtains a Subject for an entity which has the state of the passed class with the given ID.
     */
    public final <I, S extends EntityState<I>>
    EntitySubject assertEntityWithState(I id, Class<S> stateClass) {
        var found = findByState(id, stateClass);
        return EntitySubject.assertEntity(found);
    }

    private <I, S extends EntityState<I>> @Nullable Entity<I, S>
    findByState(I id, Class<S> stateClass) {
        @SuppressWarnings("unchecked")
        var repo = (Repository<I, ? extends Entity<I, S>>) repositoryOf(stateClass);
        return readOperation(() -> (Entity<I, S>) repo.find(id)
                                                      .orElse(null));
    }

    @VisibleForTesting
    final Repository<?, ?> repositoryOf(Class<? extends EntityState<?>> stateClass) {
        Repository<?, ?> repository =
                context.internalAccess()
                       .getRepository(stateClass);
        return repository;
    }

    /**
     * Asserts that there is an entity with the passed ID and the passed type of state.
     *
     * @return assertion that compares only expected fields
     */
    public final <I, S extends EntityState<I>> ProtoFluentAssertion
    assertState(I id, Class<S> stateClass) {
        checkNotNull(id);
        checkNotNull(stateClass);
        var stateAssertion =
                assertEntityWithState(id, stateClass)
                        .hasStateThat()
                        .comparingExpectedFieldsOnly();
        return stateAssertion;
    }

    /**
     * Asserts that there is an entity with the passed ID and the passed state.
     *
     * <p>The method compares only fields in the passed state.
     */
    public final <I, S extends EntityState<I>> void assertState(I id, S entityState) {
        checkNotNull(id);
        checkNotNull(entityState);
        @SuppressWarnings("unchecked")  // Guaranteed by `S` boundaries.
        var typedWithI =
                (Class<? extends EntityState<I>>) entityState.getClass();
        assertState(id, typedWithI)
                .isEqualTo(entityState);
    }

    /**
     * Obtains the subject for checking commands generated by the entities of this Bounded Context.
     */
    public final CommandSubject assertCommands() {
        return CommandSubject.assertThat(commands());
    }

    /**
     * Obtains the subject for checking events emitted by the entities of this Bounded Context.
     */
    public final EventSubject assertEvents() {
        return EventSubject.assertThat(events());
    }

    /**
     * Asserts that the context emitted only one event of the passed type.
     *
     * @param eventClass
     *         the type of the event to assert
     * @return the subject for further assertions
     */
    @CanIgnoreReturnValue
    public final ProtoFluentAssertion assertEvent(Class<? extends EventMessage> eventClass) {
        var assertEvents =
                assertEvents().withType(eventClass);
        assertEvents.hasSize(1);
        return assertEvents.message(0)
                           .comparingExpectedFieldsOnly();
    }

    /**
     * Asserts that the context emitted only one passed event.
     */
    public final void assertEvent(EventMessage event) {
        assertEvent(event.getClass()).isEqualTo(event);
    }

    /**
     * Obtains the subject for checking the {@code Query} execution result.
     */
    @SuppressWarnings("TestOnlyProblems")   /* `QueryResultSubject` is not test-only. */
    public QueryResultSubject assertQueryResult(Query query) {
        MemoizingObserver<QueryResponse> observer = memoizingObserver();
        var service = QueryService.newBuilder()
                .add(context)
                .build();
        service.read(query, observer);
        assertTrue(observer.isCompleted());

        var response = observer.firstResponse();
        return QueryResultSubject.assertQueryResult(response);
    }

    /**
     * Subscribes and activates the subscription to the passed topic.
     *
     * @param topic
     *         the topic of the subscription
     * @return a fixture for testing subscription updates.
     */
    public SubscriptionFixture subscribeTo(Topic topic) {
        var result = new SubscriptionFixture(context, topic);
        result.activate();
        return result;
    }

    /**
     * Returns a factory of {@link io.spine.client.Client Client}s
     * to this instance of {@code BlackBox}.
     */
    public BlackBoxClients clients() {
        var result = new BlackBoxClients(this, clientFactory);
        return result;
    }
}
