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
package io.spine.server.commandbus;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import io.spine.server.bus.DelegatingDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;

/**
 * A dispatcher of commands which delegates the responsibilities to
 * a {@link CommandDispatcherDelegate}.
 *
 * @see CommandDispatcherDelegate
 */
@Internal
public class DelegatingCommandDispatcher
        implements CommandDispatcher,
                   DelegatingDispatcher<CommandClass, CommandEnvelope> {

    /**
     * A target delegate.
     */
    private final CommandDispatcherDelegate delegate;

    private DelegatingCommandDispatcher(CommandDispatcherDelegate delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a new instance of {@code DelegatingCommandDispatcher}, proxying the calls
     * to the passed {@code delegate}.
     *
     * @param delegate a delegate to pass the dispatching duties to
     */
    public static DelegatingCommandDispatcher of(CommandDispatcherDelegate delegate) {
        return new DelegatingCommandDispatcher(delegate);
    }

    @Override
    public final ImmutableSet<CommandClass> messageClasses() {
        return delegate.commandClasses();
    }

    @Override
    public final DispatchOutcome dispatch(CommandEnvelope envelope) {
        return delegate.dispatchCommand(envelope);
    }

    @Override
    public CommandDispatcherDelegate delegate() {
        return delegate;
    }

    /**
     * Returns the string representation of this dispatcher.
     *
     * <p>Includes an FQN of the {@code delegate} in order to allow distinguish
     * {@code DelegatingCommandDispatcher} instances with different delegates.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("commandDelegate", delegate.getClass())
                          .toString();
    }
}
