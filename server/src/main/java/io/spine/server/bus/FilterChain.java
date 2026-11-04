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
 * the direct order of the initial {@link Deque}.
 *
 * <p>The {@link #close() close()} method closes all the underlying filters.
 *
 * <p>The acknowledgement given by a filter passes through the {@link AckConverter} of the chain
 * before the chain returns it.
 *
 * @param <E>
 *         the type of envelopes of the filtered messages
 */
final class FilterChain<E extends MessageEnvelope<?, ?, ?>> implements BusFilter<E>, Closeable {

    private final ImmutableList<BusFilter<E>> chain;
    private final AckConverter<E> converter;
    private volatile boolean closed;

    /**
     * Creates a chain which returns the acknowledgements of the filters as they are.
     */
    FilterChain(Iterable<BusFilter<E>> filters) {
        this(filters, (envelope, filter, ack) -> ack);
    }

    /**
     * Creates a chain which converts the acknowledgements of the filters.
     */
    FilterChain(Iterable<BusFilter<E>> filters, AckConverter<E> converter) {
        this.chain = ImmutableList.copyOf(filters);
        this.converter = checkNotNull(converter);
    }

    @Override
    public Optional<Ack> filter(E envelope) {
        checkNotNull(envelope);
        checkOpen();
        for (var filter : chain) {
            var output = filter.filter(envelope);
            if (output.isPresent()) {
                var ack = converter.convert(envelope, filter, output.get());
                return Optional.of(ack);
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
     * <p>The filters are closed in the reversed order compared to the invocation order.
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

    /**
     * Converts the acknowledgement given by a filter into the one the chain returns.
     *
     * @param <E>
     *         the type of envelopes of the filtered messages
     */
    @FunctionalInterface
    interface AckConverter<E extends MessageEnvelope<?, ?, ?>> {

        /**
         * Converts the acknowledgement.
         *
         * @param envelope
         *         the filtered message
         * @param filter
         *         the filter which did not let the message pass
         * @param ack
         *         the acknowledgement given by the filter
         * @return the acknowledgement to return from the chain
         */
        Ack convert(E envelope, BusFilter<E> filter, Ack ack);
    }
}
