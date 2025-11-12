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

package io.spine.server.bus;

import com.google.common.collect.ImmutableList;
import io.spine.core.Ack;
import io.spine.server.Closeable;
import io.spine.server.type.MessageEnvelope;

import java.util.Deque;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

/**
 * A {@link BusFilter} representing a chain of other bus filters.
 *
 * <p>The initial ordering of the filters is preserved. The filters are called sequentially in
 * the order direct of the initial {@link Deque}.
 *
 * <p>The {@link #close() close()} method closes all the underlying filters.
 *
 * @param <E>
 *         the type of envelopers of the filtered messages
 */
final class FilterChain<E extends MessageEnvelope<?, ?, ?>> implements BusFilter<E>, Closeable {

    private final ImmutableList<BusFilter<E>> chain;
    private volatile boolean closed;

    FilterChain(Iterable<BusFilter<E>> filters) {
        this.chain = ImmutableList.copyOf(filters);
    }

    @Override
    public Optional<Ack> filter(E envelope) {
        checkNotNull(envelope);
        checkOpen();
        for (var filter : chain) {
            var output = filter.filter(envelope);
            if (output.isPresent()) {
                return output;
            }
        }
        return letPass();
    }

    boolean contains(BusFilter<E> filter) {
        return chain.contains(filter);
    }

    /**
     * Closes all the filters in the chain and deletes them from the chain.
     *
     * <p>The filters are closed in the reversed order comparing to the invocation order.
     *
     * @throws IllegalStateException
     *         on a repetitive call
     * @see #isOpen()
     */
    @Override
    public void close() {
        checkOpen();
        closed = true;
        for (BusFilter<?> filter : chain.reverse()) {
            filter.close();
        }
    }

    /**
     * Tells if this filter chain is open.
     *
     * @see #close()
     */
    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public String toString() {
        var filters = chain.stream()
                .map(BusFilter::getClass)
                .map(Class::getSimpleName)
                .collect(joining(", "));
        return format("%s[%s]", FilterChain.class.getName(), filters);
    }
}
