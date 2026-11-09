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
import io.spine.server.bus.InvalidMessageHandler;
import io.spine.server.bus.UnicastBus;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.SystemWriteSide;
import io.spine.system.server.event.CommandValidationFailed;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.system.server.WriteSideFunction.delegatingTo;

/**
 * Dispatches the incoming commands to the corresponding handler.
 */
@Internal
public final class CommandBus
        extends UnicastBus<Command, CommandEnvelope, CommandClass, CommandDispatcher> {

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

    private final DeadCommandHandler deadCommandHandler;

    /**
     * The validator for the commands posted into this bus.
     *
     * <p>The value is effectively final, though should be initialized lazily.
     *
     * @see #validator() to retrieve the non-null value of the validator
     */
    @LazyInit
    private @MonotonicNonNull CommandValidator commandValidator;

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
        this.watcher = checkNotNull(builder.watcher);
        this.deadCommandHandler = new DeadCommandHandler(this::systemFor);
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
     * Places a {@link CommandReceivedTap} and a {@link TenantFilter} first, and
     * a {@link CommandScheduler} last in the filter chain.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    protected Iterable<BusFilter<CommandEnvelope>>
    setupFilters(Iterable<BusFilter<CommandEnvelope>> filters) {
        var fromSuper = super.setupFilters(filters);
        return ImmutableList.<BusFilter<CommandEnvelope>>builder()
                .add(new CommandReceivedTap(this::systemFor))
                .add(new TenantFilter(multitenant, this::systemFor))
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
            commandValidator = new CommandValidator();
        }
        return commandValidator;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Records the violations in the System context.
     */
    @Override
    protected InvalidMessageHandler<CommandEnvelope> invalidMessageHandler() {
        return (command, violations) -> {
            var failed = CommandValidationFailed.newBuilder()
                    .setId(command.id())
                    .setValidationError(violations)
                    .build();
            systemFor(command.tenantId()).postEvent(failed, command.asMessageOrigin());
        };
    }

    /**
     * Passes a previously scheduled command to the corresponding dispatcher.
     */
    void postPreviouslyScheduled(Command command) {
        var commandEnvelope = CommandEnvelope.of(command);
        dispatch(commandEnvelope);
    }

    /**
     * Does nothing, because the {@code CommandBus} does not store commands.
     */
    @Override
    protected void store(Iterable<Command> commands) {
        // Commands are not stored.
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

            commandScheduler =
                    ServerEnvironment.instance()
                                     .newCommandScheduler();

            if (watcher == null) {
                watcher = new FlightRecorder(
                        (tenantId) -> delegatingTo(writeSide).get(tenantId)
                );
            }
            commandScheduler.setWatcher(watcher);

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
