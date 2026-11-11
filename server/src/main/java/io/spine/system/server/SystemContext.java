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

package io.spine.system.server;

import io.spine.annotation.Internal;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.delivery.CatchUpRepository;
import io.spine.server.delivery.ShardMaintenanceRepository;
import io.spine.server.event.EventDispatcher;
import io.spine.server.trace.TracerFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * An implementation of {@link BoundedContext} used for the System domain.
 *
 * <p>Orchestrates the system entities that serve the goal of monitoring, auditing, and debugging
 * the domain-specific entities. It also runs the catch-up of the projections
 * of the associated Bounded Context.
 *
 * <p>Each {@link BoundedContext} has an associated {@code SystemContext}. The system entities
 * describe the meta information about the domain entities of the associated {@link BoundedContext}.
 * A system bounded context does NOT have an associated bounded context.
 *
 * @apiNote The framework users should not access a System Bounded Context directly.
 * Programmers extending the framework should see {@link SystemClient} for the front-facing API
 * of the System bounded context.
 *
 * @see SystemClient
 * @see BoundedContext
 */
@Internal
public final class SystemContext extends BoundedContext {

    private final SystemConfig config;

    /**
     * The domain context served by this System context.
     *
     * <p>Remains {@code null} until the domain context is built.
     */
    private @MonotonicNonNull BoundedContext domain;

    private SystemContext(BoundedContextBuilder builder) {
        super(builder);
        this.config = builder.systemSettings()
                             .freeze();
    }

    /**
     * Creates a new instance of {@code SystemBoundedContext} from the given
     * {@link BoundedContextBuilder}.
     *
     * @param builder the configuration of the instance to create
     * @return new {@code SystemBoundedContext}
     */
    public static SystemContext newInstance(BoundedContextBuilder builder) {
        var commandLog = new CommandLogRepository();
        var enricher = SystemEnricher.create(commandLog);
        builder.enrichEventsUsing(enricher);
        var result = new SystemContext(builder);
        // Registers the buses with the context, as a domain context does,
        // before the repositories are added.
        result.init();
        result.registerRepositories(commandLog);
        var tracerFactory = builder.getTracerFactory();
        if (tracerFactory != null) {
            result.registerTracing(tracerFactory);
        }
        return result;
    }

    /**
     * Registers the repositories of the system entities.
     *
     * <p>The repositories of the catch-up and shard maintenance processes are always
     * registered. Together, they perform the catch-up of the projections of
     * the associated domain context.
     */
    private void registerRepositories(CommandLogRepository commandLog) {
        register(new CatchUpRepository());
        register(new ShardMaintenanceRepository());
        if (config.includeCommandLog()) {
            register(commandLog);
            register(new ScheduledCommandRepository());
        }
    }

    private void registerTracing(TracerFactory factory) {
        EventDispatcher observer = new TraceEventObserver(spec(), factory);
        registerEventDispatcher(observer);
    }

    /**
     * Creates a client for this context.
     *
     * <p>The resulting {@link SystemClient} is the way for the domain context to access its system
     * counterpart.
     *
     * @return new instance of {@link SystemClient}
     */
    public SystemClient createClient() {
        return new DefaultSystemClient(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Since the System context does not have an associated System context of its own,
     * the method returns a {@link NoOpSystemWriteSide} instance.
     */
    @Override
    public NoOpSystemClient systemClient() {
        return NoOpSystemClient.INSTANCE;
    }

    /**
     * Tells this context which domain context it serves.
     *
     * <p>The builder of the domain context calls this method once that context is built.
     *
     * @throws IllegalStateException
     *         if the domain context is already set
     * @apiNote This method is {@code public} because the builder of the domain context
     *         belongs to another package.
     */
    public void attachDomain(BoundedContext domain) {
        checkNotNull(domain);
        checkState(this.domain == null,
                   "The System context `%s` already serves the domain context `%s`," +
                           " and cannot serve `%s`.", this, this.domain, domain);
        this.domain = domain;
    }

    /**
     * Returns the domain context this System context serves.
     *
     * @throws IllegalStateException
     *         if this System context does not know its domain context yet
     */
    @Override
    protected BoundedContext domainContext() {
        checkState(domain != null,
                   "The System context `%s` does not know its domain context yet.", this);
        return domain;
    }

    /**
     * Obtains the configuration of this system context.
     */
    SystemConfig config() {
        return config;
    }
}
