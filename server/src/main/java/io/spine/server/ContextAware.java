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

package io.spine.server;

import io.spine.annotation.Internal;

import javax.annotation.OverridingMethodsMustInvokeSuper;

import static com.google.common.base.Preconditions.checkState;

/**
 * A structural part of a Bounded Context which is aware of the other parts.
 */
@Internal
public interface ContextAware {

    /**
     * Registers this instance as a part of the given Bounded Context.
     *
     * <p>This method should be only called once. However, it is allowed to register a single
     * instance many times with the <strong>same</strong> Bounded Context. If the same instance is
     * registered with different Contexts, a runtime exception is thrown.
     *
     * @param context
     *         the Context to which this instance belongs
     */
    void registerWith(BoundedContext context);

    /**
     * Determines if this instance is already registered with a Bounded Context.
     */
    boolean isRegistered();

    /**
     * Unregisters this instance from the Bounded Context with which this instance
     * was {@linkplain #registerWith(BoundedContext) registered before}.
     *
     * <p>The default implementation checks if this instance is registered and does nothing else.
     * Override this method to perform additional actions, making sure to call the super method.
     */
    @OverridingMethodsMustInvokeSuper
    default void unregister() {
        checkRegistered();
    }

    /**
     * Verifies that this instance is already registered.
     *
     * <p>Throws an {@code IllegalStateException} if not registered.
     */
    default void checkRegistered() {
        checkState(isRegistered(), "%s is NOT registered.", this);
    }

    /**
     * Verifies that this instance is NOT registered yet.
     *
     * <p>Throws an {@code IllegalStateException} if already registered.
     */
    default void checkNotRegistered() {
        checkState(!isRegistered(), "`%s` is already registered.", this);
    }
}
