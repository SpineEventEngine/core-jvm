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

package io.spine.server;

import io.spine.server.entity.Repository;
import io.spine.system.server.SystemClient;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfUnchecked;

/**
 * A bounded context representing a user-specific domain model.
 *
 * <p>The {@link BoundedContext} instances typically seen to the users
 * (i.e. built with a {@link BoundedContextBuilder}) are instances of this class.
 *
 * <p>All the user interactions with the system (such as
 * {@linkplain BoundedContext#register(Repository) repository registration},
 * {@linkplain BoundedContext#commandBus() command posting},
 * {@linkplain BoundedContext.InternalAccess#findRepository(Class) query processing}, etc.) happen
 * through an instance of this class.
 *
 * <p>Each {@code DomainContext} has an associated
 * {@link io.spine.system.server.SystemContext SystemContext}, which manages the meta-information
 * about entities of this Bounded Context.
 *
 * @see io.spine.system.server.SystemContext SystemContext
 */
final class DomainContext extends BoundedContext {

    private final SystemClient system;

    /**
     * The action to run once this context and its System context are closed.
     *
     * <p>Holds {@code null} until the action is {@linkplain #runAfterClose(Runnable) set},
     * and again once it has run, so that the action runs once.
     */
    private final AtomicReference<@Nullable Runnable> afterClose = new AtomicReference<>();

    private DomainContext(BoundedContextBuilder builder, SystemClient system) {
        super(builder);
        this.system = checkNotNull(system);
    }

    static DomainContext newInstance(BoundedContextBuilder builder, SystemClient system) {
        checkNotNull(builder);
        checkNotNull(system);

        var result = new DomainContext(builder, system);
        result.init();
        return result;
    }

    @Override
    public SystemClient systemClient() {
        return system;
    }

    /**
     * Returns this context, which is its own domain context.
     */
    @Override
    protected BoundedContext domainContext() {
        return this;
    }

    /**
     * Sets the action to run once this context and its System context are closed.
     *
     * <p>{@link BoundedContextBuilder#build()} sets an action that closes the application it
     * creates for a test-only context, since nothing else can reach that application. By the time
     * the action runs, the context is closed, so the application does not close it again.
     *
     * @throws IllegalStateException
     *         if an action is already set
     */
    void runAfterClose(Runnable action) {
        checkNotNull(action);
        checkState(afterClose.compareAndSet(null, action),
                   "The context `%s` already has an action to run after it is closed.", this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the System context as well, and then runs the action
     * {@linkplain #runAfterClose(Runnable) set} to run after closing, if any. Each step is taken
     * even if an earlier one fails. The first failure is thrown, with the later ones added to it
     * as suppressed.
     */
    @Override
    public void close() {
        var failure = runKeepingFailure(super::close, null);
        failure = runKeepingFailure(system::closeSystemContext, failure);
        var action = afterClose.getAndSet(null);
        if (action != null) {
            failure = runKeepingFailure(action, failure);
        }
        if (failure != null) {
            throwIfUnchecked(failure);
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Runs the given step, and returns the first failure among the given one and the one
     * of the step.
     *
     * <p>If both failed, the failure of the step is added to the given one as suppressed,
     * unless it is that very failure, since an exception cannot suppress itself.
     */
    @SuppressWarnings("ObjectEquality") // Need instances here.
    private static @Nullable Throwable runKeepingFailure(Runnable step,
                                                         @Nullable Throwable failure) {
        try {
            step.run();
            return failure;
        } catch (Throwable t) {
            // Keeps any failure, an `Error` too, so that the later steps still run.
            if (failure == null) {
                return t;
            }
            if (failure != t) {
                failure.addSuppressed(t);
            }
            return failure;
        }
    }
}
