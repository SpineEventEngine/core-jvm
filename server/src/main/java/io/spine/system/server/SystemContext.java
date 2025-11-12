/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.system.server;

import io.spine.annotation.Internal;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.event.EventDispatcher;

/**
 * An implementation of {@link BoundedContext} used for the System domain.
 *
 * <p>Orchestrates the system entities that serve the goal of monitoring, auditing, and debugging
 * the domain-specific entities.
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
        result.registerRepositories(commandLog);
        result.registerTracing();
        result.init();
        return result;
    }

    private void registerRepositories(CommandLogRepository commandLog) {
        if (config.includeCommandLog()) {
            register(commandLog);
            register(new ScheduledCommandRepository());
        }
    }

    private void registerTracing() {
        var tracing = ServerEnvironment.instance().tracing();
        tracing.ifPresent(factory -> {
            EventDispatcher observer = new TraceEventObserver(spec(), factory);
            registerEventDispatcher(observer);
        });
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
     * Obtains the configuration of this system context.
     */
    SystemConfig config() {
        return config;
    }
}
