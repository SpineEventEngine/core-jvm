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

import io.spine.core.Ack;
import io.spine.core.Signal;
import io.spine.server.type.MessageEnvelope;
import io.spine.server.type.SignalEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

/**
 * The abstract test suite for the tests of the builders of buses.
 *
 * @see io.spine.server.commandbus.CommandBusBuilderTest
 * @see io.spine.server.event.EventBusBuilderTest
 */
public abstract class BusBuilderTest<B extends BusBuilder<?, T, E, ?, ?>,
                                     E extends SignalEnvelope<?, T, ?>,
                                     T extends Signal<?, ?, ?>> {

    protected abstract B builder();

    @Test
    @DisplayName("allow adding filter")
    void allowAddingFilter() {
        BusFilter<E> filter = new StubFilter<>();

        assertThat(builder().appendFilter(filter)
                            .filters())
                .contains(filter);
    }

    @SuppressWarnings("CheckReturnValue") // calling builder
    @Test
    @DisplayName("preserve filters order")
    void preserveFiltersOrder() {
        BusFilter<E> first = new StubFilter<>();
        BusFilter<E> second = new StubFilter<>();

        var builder = builder();
        builder.appendFilter(first)
               .appendFilter(second);
        var filters = builder.filters();

        assertThat(filters)
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("add listener")
    void addingListener() {
        Listener<E> listener = (e) -> {};

        assertThat(builder().addListener(listener)
                            .listeners())
                .contains(listener);
    }

    @Test
    @DisplayName("remove listener")
    void removingListener() {
        Listener<E> listener = (e) -> {};

        assertThat(builder().addListener(listener)
                            .removeListener(listener)
                            .listeners())
                .doesNotContain(listener);
    }

    /**
     * Stub implementation of {@code BusFilter} that always returns empty {@code Optional}.
     */
    private static final class StubFilter<E extends MessageEnvelope<?, ?, ?>>
            implements BusFilter<E> {

        @Override
        public Optional<Ack> filter(E envelope) {
            return letPass();
        }
    }
}
