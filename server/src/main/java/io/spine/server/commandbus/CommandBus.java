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

package io.spine.server.commandbus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.errorprone.annotations.concurrent.LazyInit;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.TenantId;
import io.spine.grpc.CompositeObserver;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.bus.BusBuilder;
import io.spine.server.bus.BusFilter;
import io.spine.server.bus.DeadMessageHandler;
import io.spine.server.bus.EnvelopeValidator;
import io.spine.server.bus.UnicastBus;
import io.spine.server.event.EventBus;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.SystemWriteSide;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.system.server.WriteSideFunction.delegatingTo;

/**
 * Dispatches the incoming commands to the corresponding handler.
 */
@Internal
public final class CommandBus
        extends UnicastBus<Command, CommandEnvelope, CommandClass, CommandDispatcher> {

    /** Consumes tenant IDs from incoming commands. */
    private final Consumer<TenantId> tenantConsumer;

    /** Consumes commands dispatched by this bus. */
    private final CommandFlowWatcher watcher;

    private final CommandScheduler scheduler;
    private final SystemWriteSide systemWriteSide;

    /**
     * Is {@code true}, if the {@code BoundedContext} (to which this {@code CommandBus} belongs)
     * is multi-tenant.
     *
     * <p>If the {@code CommandBus} is multi-tenant, the commands posted must have the
     * {@code tenant_id} attribute defined.
     */
    private final boolean multitenant;

    @SuppressWarnings("FieldMayBeStatic") // It's better to keep instance, e.g., for tests.
    private final DeadCommandHandler deadCommandHandler = new DeadCommandHandler();

    /**
     * The validator for the commands posted into this bus.
     *
     * <p>The value is effectively final, though should be initialized lazily.
     *
     * @see #validator() to getreive the non-null value of the validator
     */
    @LazyInit
    private @MonotonicNonNull CommandValidator commandValidator;

    /**
     * An observer which processes the {@linkplain io.spine.base.RejectionThrowable rejections}
     * thrown by the bus {@linkplain BusFilter filters}.
     *
     * <p>When the bus is first created, this observer does nothing. Once an {@link EventBus} is
     * {@linkplain #initObservers(EventBus) injected} into this command bus instance, the observer
     * will start publishing the rejections to the said event bus.
     *
     * <p>This observer can stay NO-OP in a test environment for the simplicity of tests.
     */
    private StreamObserver<Ack> immediateRejectionObserver = noOpObserver();

    /**
     * Creates a new instance according to the passed {@link Builder}.
     */
    private CommandBus(Builder builder) {
        super(builder);
        this.multitenant = builder.multitenant != null
                           ? builder.multitenant
                           : false;
        this.scheduler = checkNotNull(builder.commandScheduler);
        this.systemWriteSide = builder.ensureSystem();
        this.tenantConsumer = checkNotNull(builder.tenantConsumer);
        this.watcher = checkNotNull(builder.watcher);
    }

    /**
     * Creates a new {@link Builder} for the {@code CommandBus}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    @VisibleForTesting
    public boolean isMultitenant() {
        return multitenant;
    }

    /**
     * Initializes the {@link #immediateRejectionObserver} with the given {@link EventBus}.
     *
     * <p>This enables posting of rejections received from command filters
     * to the associated {@link EventBus}.
     *
     * <p>This method is called by the {@link io.spine.server.BoundedContext BoundedContext} and is
     * a part of context initialization process.
     *
     * @see AckRejectionPublisher
     */
    public void initObservers(EventBus eventBus) {
        checkNotNull(eventBus);
        immediateRejectionObserver = new AckRejectionPublisher(eventBus);
    }

    /**
     * Places a {@link CommandReceivedTap} first and a {@link CommandScheduler} last
     * in the filter chain.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    protected Iterable<BusFilter<CommandEnvelope>>
    setupFilters(Iterable<BusFilter<CommandEnvelope>> filters) {
        var fromSuper = super.setupFilters(filters);
        return ImmutableList.<BusFilter<CommandEnvelope>>builder()
                .add(new CommandReceivedTap(this::systemFor))
                .addAll(fromSuper)
                .add(scheduler)
                .build();
    }

    @Override
    protected CommandEnvelope toEnvelope(Command message) {
        return CommandEnvelope.of(message);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wraps the {@code source} observer with the {@link CommandAckMonitor}.
     *
     * @return a new instance of {@link CommandAckMonitor} with the given parameters
     */
    @Override
    protected StreamObserver<Ack> prepareObserver(Iterable<Command> commands,
                                                  StreamObserver<Ack> source) {
        var wrappedSource = super.prepareObserver(commands, source);
        var tenant = tenantOf(commands);
        var commandAckMonitor = CommandAckMonitor.newBuilder()
                .setTenantId(tenant)
                .setPostedCommands(ImmutableSet.copyOf(commands))
                .setSystemWriteSide(systemWriteSide)
                .build();
        var result = new CompositeObserver<>(ImmutableList.of(
                wrappedSource,
                immediateRejectionObserver,
                commandAckMonitor
        ));
        return result;
    }

    private static TenantId tenantOf(Iterable<Command> commands) {
        var iterator = commands.iterator();
        return iterator.hasNext()
               ? iterator.next().tenant()
               : TenantId.getDefaultInstance();
    }

    private SystemWriteSide systemFor(TenantId tenantId) {
        checkNotNull(tenantId);
        var result = delegatingTo(systemWriteSide).get(tenantId);
        return result;
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored"
            /* Dispatching outcome is reported via system events, if needed. */)
    protected void dispatch(CommandEnvelope command) {
        var dispatcher = dispatcherOf(command);
        watcher.onDispatchCommand(command);
        dispatcher.dispatch(command);
    }

    /**
     * Obtains the view {@code Set} of commands that are known to this {@code CommandBus}.
     *
     * <p>This set is changed when command dispatchers or handlers are registered or un-registered.
     *
     * @return a set of classes of supported commands
     */
    @Internal
    public Set<CommandClass> registeredCommandClasses() {
        return registry().registeredMessageClasses();
    }

    @Override
    protected DeadMessageHandler<CommandEnvelope> deadMessageHandler() {
        return deadCommandHandler;
    }

    @Override
    protected EnvelopeValidator<CommandEnvelope> validator() {
        if (commandValidator == null) {
            commandValidator = new CommandValidator(this);
        }
        return commandValidator;
    }

    /**
     * Passes a previously scheduled command to the corresponding dispatcher.
     */
    void postPreviouslyScheduled(Command command) {
        var commandEnvelope = CommandEnvelope.of(command);
        dispatch(commandEnvelope);
    }

    @Override
    protected void store(Iterable<Command> commands) {
        var tenantId = tenantOf(commands);
        tenantConsumer.accept(tenantId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides for return type covariance.
     */
    @Override
    protected CommandDispatcherRegistry registry() {
        return (CommandDispatcherRegistry) super.registry();
    }

    /**
     * The {@code Builder} for {@code CommandBus}.
     */
    public static class Builder extends BusBuilder<Builder,
                                                   Command,
                                                   CommandEnvelope,
                                                   CommandClass,
                                                   CommandDispatcher> {

        /**
         * The multi-tenancy flag for the {@code CommandBus} to build.
         *
         * <p>The value of this field should be equal to that of corresponding
         * {@linkplain BoundedContextBuilder BoundedContext.Builder} and is not
         * supposed to be {@linkplain #setMultitenant(Boolean) set directly}.
         *
         * <p>If set directly, the value would be matched to the multi-tenancy flag of
         * {@code BoundedContext}.
         */
        private @Nullable Boolean multitenant;
        private @Nullable CommandFlowWatcher watcher;
        private @MonotonicNonNull Consumer<TenantId> tenantConsumer;
        private @MonotonicNonNull CommandScheduler commandScheduler;

        /** Prevents direct instantiation. */
        private Builder() {
            super();
        }

        @Override
        protected CommandDispatcherRegistry newRegistry() {
            return new CommandDispatcherRegistry();
        }

        @Internal
        @CanIgnoreReturnValue
        public Builder setMultitenant(@Nullable Boolean multitenant) {
            this.multitenant = multitenant;
            return this;
        }

        @Internal
        @CanIgnoreReturnValue
        public Builder setWatcher(CommandFlowWatcher watcher) {
            this.watcher = watcher;
            return this;
        }

        /**
         * Builds an instance of {@link CommandBus}.
         *
         * <p>This method is supposed to be called internally when building an enclosing
         * {@code BoundedContext}.
         */
        @Override
        @CheckReturnValue
        public CommandBus build() {
            var writeSide = ensureSystem();
            var tenantIndex = ensureTenantIndex();

            commandScheduler =
                    ServerEnvironment.instance()
                                     .newCommandScheduler();

            if (watcher == null) {
                watcher = new FlightRecorder(
                        (tenantId) -> delegatingTo(writeSide).get(tenantId)
                );
            }
            commandScheduler.setWatcher(watcher);

            tenantConsumer = tenantIndex::keep;

            var commandBus = createCommandBus();

            commandScheduler.setCommandBus(commandBus);

            return commandBus;
        }

        @Override
        @CheckReturnValue
        protected Builder self() {
            return this;
        }

        @CheckReturnValue
        @SuppressWarnings("CheckReturnValue")
        private CommandBus createCommandBus() {
            var commandBus = new CommandBus(this);
            return commandBus;
        }
    }
}
