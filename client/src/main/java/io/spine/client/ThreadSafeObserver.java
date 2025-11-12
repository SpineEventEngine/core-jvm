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

package io.spine.client;

import io.grpc.stub.StreamObserver;

import javax.annotation.concurrent.ThreadSafe;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A thread-safe {@code StreamObserver}.
 *
 * <p>Wraps another {@code StreamObserver}, delegating all calls to it in a thread-safe way.
 *
 * @param <V>
 *         the type of the observed values
 */
@ThreadSafe
public final class ThreadSafeObserver<V> implements StreamObserver<V> {

    private final StreamObserver<V> wrapped;

    /**
     * Creates a new instance of {@code ThreadSafeObserver} by wrapping the passed instance
     * and delegating all the calls to it.
     *
     * @param observer
     *         the observer to wrap into a thread-safe shell
     */
    public ThreadSafeObserver(StreamObserver<V> observer) {
        this.wrapped = checkNotNull(observer);
    }

    @Override
    public synchronized void onNext(V value) {
        wrapped.onNext(value);
    }

    @Override
    public synchronized void onError(Throwable t) {
        wrapped.onError(t);
    }

    @Override
    public synchronized void onCompleted() {
        wrapped.onCompleted();
    }
}
