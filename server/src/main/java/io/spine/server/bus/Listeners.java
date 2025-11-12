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

import io.spine.annotation.VisibleForTesting;
import io.spine.server.type.SignalEnvelope;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.synchronizedSet;

/**
 * Manages consumption of a message posted to the bus by its listeners.
 *
 * @param <E> the type of the {@link SignalEnvelope} posted by the bus
 */
final class Listeners<E extends SignalEnvelope<?, ?, ?>> implements Consumer<E> {

    private final Set<Listener<E>> listeners = synchronizedSet(new HashSet<>());

    Listeners(BusBuilder<?, ?, E, ?, ?> builder) {
        checkNotNull(builder);
        listeners.addAll(builder.listeners());
    }

    void add(Listener<E> listener) {
        checkNotNull(listener);
        listeners.add(listener);
    }

    void remove(Listener<E> listener) {
        checkNotNull(listener);
        listeners.remove(listener);
    }

    @Override
    public void accept(E envelope) {
        listeners.forEach(listener -> {
            try {
                listener.accept(envelope);
            } catch (RuntimeException ignored) {
                // Do nothing.
            }
        });
    }

    @VisibleForTesting
    boolean contains(Listener<E> listener) {
        return listeners.contains(listener);
    }
}
