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

package io.spine.server.commandbus.given;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

/**
 * A {@link ScheduledExecutorService} which immediately invokes all scheduled actions in the caller
 * thread.
 *
 * <p>In tests, the delayed execution of operations can often lead to errors and test output
 * pollution, or even failures.
 *
 * <p>For such cases, the {@code DirectScheduledExecutor} provides convenience
 * {@code schedule(...)} methods.
 *
 * <p>All other methods just invoke the
 * {@linkplain com.google.common.util.concurrent.MoreExecutors#newDirectExecutorService() direct
 * executor service} delegate.
 */
public final class DirectScheduledExecutor implements ScheduledExecutorService {

    private final ExecutorService delegate = newDirectExecutorService();

    /**
     * Immediately invokes the given command in the caller thread.
     *
     * <p>Returns {@code null} as the result of {@code schedule} for command bus is always
     * ignored.
     */
    @Override
    public @Nullable ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        execute(command);
        return null;
    }

    /**
     * Immediately invokes the given callable in the caller thread.
     *
     * <p>Returns {@code null} as the result of {@code schedule} for command bus is always
     * ignored.
     */
    @SuppressWarnings({"CheckReturnValue", "ResultOfMethodCallIgnored", "FutureReturnValueIgnored"})
    // In these tests, we do not really care about the schedule result.
    @Override
    public <V> @Nullable ScheduledFuture<V>
    schedule(Callable<V> callable, long delay, TimeUnit unit) {
        submit(callable);
        return null;
    }

    /**
     * Immediately invokes the given command in the caller thread.
     *
     * <p>Returns {@code null} as the result of {@code schedule} for command bus is always
     * ignored.
     */
    @Override
    public @Nullable ScheduledFuture<?>
    scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        execute(command);
        return null;
    }

    /**
     * Immediately invokes the given command in the caller thread.
     *
     * <p>Returns {@code null} as the result of {@code schedule} for command bus is always
     * ignored.
     */
    @Override
    public @Nullable ScheduledFuture<?>
    scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        execute(command);
        return null;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(task);
    }

    @Override
    public <T> List<Future<T>>
    invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>>
    invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }
}
