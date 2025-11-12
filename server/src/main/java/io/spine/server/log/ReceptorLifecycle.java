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

package io.spine.server.log;

import io.spine.server.model.Receptor;

/**
 * Callbacks for a {@link Receptor} invocation.
 *
 * <p>If the target of the method implements {@code HandlerLifecycle}, it is invoked alongside
 * handler method. For example:
 * <pre>
 * class SignUpSubscriber extends AbstractEventSubscriber implements HandlerLifecycle {
 *
 *    {@literal @Subscribe}
 *     void on(UserSignedUp event) {
 *         // ...
 *     }
 *
 *    {@literal @Override                                                       }
 *    {@literal public void beforeInvoke(HandlerMethod<?, ?, ?, ?> method) {    }
 *         // ...
 *     }
 *
 *    {@literal @Override                                                       }
 *    {@literal public void afterInvoke(HandlerMethod<?, ?, ?, ?> method) {     }
 *         // ...
 *     }
 * }
 * </pre>
 *
 * <p>When a {@code UserSignedUp} event is dispatched to the {@code SignUpSubscriber},
 * the invocation order goes as follows:
 * <ol>
 *     <li>{@code beforeInvoke([instance representing on(UserSignedUp) method])}.
 *     <li>{@code on(UserSignedUp)}.
 *     <li>{@code afterInvoke([instance representing on(UserSignedUp) method])}.
 * </ol>
 *
 * @param <T> the type of the target object exposing the receptor method
 */
public interface ReceptorLifecycle<T> {

    /**
     * A callback for a handler method invocation start.
     *
     * <p>The handler method is invoked immediately after this method.
     */
    void beforeInvoke(Receptor<T, ?, ?, ?> method);

    /**
     * A callback for a handler method invocation end.
     *
     * <p>This method is invoked immediately after the handler method, even if it has thrown
     * an exception.
     */
    void afterInvoke(Receptor<T, ?, ?, ?> method);
}
