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

package io.spine.server.log;

import io.spine.server.model.Receptor;

/**
 * Callbacks for a {@link Receptor} invocation.
 *
 * <p>If the target of the method implements {@code HandlerLifecycle}, it is invoked alongside
 * the handler method. For example:
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
    void beforeInvoke(Receptor<T, ?, ?> method);

    /**
     * A callback for a handler method invocation end.
     *
     * <p>This method is invoked immediately after the handler method, even if it has thrown
     * an exception.
     */
    void afterInvoke(Receptor<T, ?, ?> method);
}
