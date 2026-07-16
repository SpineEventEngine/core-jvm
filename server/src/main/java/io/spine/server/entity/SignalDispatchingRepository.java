/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.base.EntityState;
import io.spine.server.BoundedContext;
import io.spine.server.commandbus.CommandDispatcherDelegate;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.setup.CommandRoutingSetup;
import io.spine.server.type.CommandEnvelope;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.dispatch.DispatchOutcomes.maybeSentToInbox;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static io.spine.util.Suppliers2.memoize;

/**
 * Abstract base for repositories that dispatch signals — both events and commands —
 * to the entities they manage.
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <E>
 *         the type of managed entities
 * @param <S>
 *         the type of the entity state
 */
public abstract class SignalDispatchingRepository<I,
                                                  E extends AbstractEntity<I, S>,
                                                  S extends EntityState<I>>
        extends EventDispatchingRepository<I, E, S>
        implements CommandDispatcherDelegate {

    /** The command routing schema used by this repository. */
    private final Supplier<CommandRouting<I>> commandRouting;

    protected SignalDispatchingRepository() {
        super();
        this.commandRouting = memoize(() -> CommandRouting.newInstance(idClass()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registers with the {@code CommandBus} for dispatching commands
     * (via a {@linkplain io.spine.server.commandbus.DelegatingCommandDispatcher
     * delegating dispatcher}), and sets up the routing of the commands.
     *
     * @param context
     *         the {@code BoundedContext} of this repository
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        doSetupCommandRouting();
    }

    private void doSetupCommandRouting() {
        var cmdRouting = commandRouting();
        var entityClass = entityClass();
        CommandRoutingSetup.apply(entityClass, cmdRouting);
        setupCommandRouting(cmdRouting);
    }

    /**
     * A callback for derived classes to customize routing schema for commands.
     *
     * <p>Default routing returns the value of the first field of a command message.
     *
     * @param routing
     *         the routing schema to customize
     */
    protected abstract void setupCommandRouting(CommandRouting<I> routing);

    /**
     * Obtains command routing schema used by this repository.
     */
    private CommandRouting<I> commandRouting() {
        return commandRouting.get();
    }

    /**
     * Dispatches the command to a corresponding entity.
     *
     * <p>If there is no stored entity with such an ID,
     * a new entity is created and stored after it handles the passed command.
     *
     * @param command
     *         the command to dispatch
     */
    @Override
    public final DispatchOutcome dispatchCommand(CommandEnvelope command) {
        checkNotNull(command);
        var target = route(command);
        target.ifPresent(id -> inbox().send(command)
                                      .toHandler(id));
        return maybeSentToInbox(command, target);
    }

    private Optional<I> route(CommandEnvelope cmd) {
        var target = route(commandRouting(), cmd);
        target.ifPresent(id -> onCommandTargetSet(id, cmd));
        return target;
    }

    private void onCommandTargetSet(I id, CommandEnvelope cmd) {
        var lifecycle = lifecycleOf(id);
        var commandId = cmd.id();
        with(cmd.tenantId())
                .run(() -> lifecycle.onTargetAssignedToCommand(commandId));
    }
}
