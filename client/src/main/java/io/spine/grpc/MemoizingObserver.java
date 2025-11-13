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

package io.spine.grpc;

import io.grpc.stub.StreamObserver;
import io.spine.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;

/**
 * The {@link StreamObserver} which stores the input data and then exposes it via API calls.
 *
 * @param <T> the type of streamed objects
 */
@Internal
public class MemoizingObserver<T> implements StreamObserver<T> {
    
    private final List<T> responses = newLinkedList();
    private @Nullable Throwable throwable;
    private boolean completed = false;

    /**
     * Creates a new empty instance.
     */
    public MemoizingObserver() {
    }

    @Override
    public void onNext(T value) {
        responses.add(value);
    }

    @Override
    public void onError(Throwable t) {
        throwable = t;
    }

    @Override
    public void onCompleted() {
        completed = true;
    }

    /**
     * Returns the first item which has been fed to this {@code StreamObserver}.
     *
     * <p>If there were no responses yet, {@link IllegalStateException} is thrown.
     *
     * @return the first item responded
     */
    public T firstResponse() {
        var noResponses = responses.isEmpty();
        if (noResponses) {
            throw new IllegalStateException("No responses have been received yet.");
        }
        return responses.get(0);
    }

    /**
     * Returns all the responses received so far.
     *
     * @return a mutable list with all responses received by this {@code StreamObserver}
     */
    public List<T> responses() {
        return newArrayList(responses);
    }

    /**
     * Tells if the response has been completed.
     *
     * @return {@code true} if the response has been completed, {@code false} otherwise
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Obtains the {@linkplain Throwable error} if it has been received by this observer.
     *
     * @return an error, or {@code null} if no error has been received
     */
    public @Nullable Throwable getError() {
        return throwable;
    }
}
