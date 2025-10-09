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

package io.spine.testing.server.blackbox.probe;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.Message;
import io.spine.core.Signal;
import io.spine.core.SignalId;
import io.spine.core.TenantId;
import io.spine.server.bus.Listener;
import io.spine.server.type.MessageEnvelope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Collections.synchronizedList;
import static java.util.Collections.synchronizedMap;
import static java.util.Optional.ofNullable;

/**
 * Abstract base for message listeners that collect messages posted to a bus.
 *
 * @param <I>
 *         the type of the signal identifiers
 * @param <T>
 *         the type of the collected signals
 * @param <E>
 *         the type of the message envelopes
 */
abstract class MessageCollector<I extends SignalId,
                                T extends Signal<I, ?, ?>,
                                E extends MessageEnvelope<I, T, ?>>
        implements Listener<E> {

    private final List<T> outerObjects = synchronizedList(new ArrayList<>());
    private final Map<I, Message> messages = synchronizedMap(new HashMap<>());

    /**
     * Looks up the command message by the command ID.
     */
    public final <M extends Message> Optional<M> find(I messageId, Class<M> messageClass) {
        var commandMessage = messages.get(messageId);
        ProtoTruth.assertThat(commandMessage)
                  .isInstanceOf(messageClass);
        @SuppressWarnings("unchecked") // Checked with an assertion.
        var result = (M) commandMessage;
        return ofNullable(result);
    }

    /**
     * Remembers the passed message and accepts its, returning empty {@code Optional}.
     */
    @Override
    public final void accept(E envelope) {
        messages.put(envelope.id(), envelope.message());
        outerObjects.add(envelope.outerObject());
    }

    /**
     * Obtains an immutable list with outer objects of messages collected so far.
     */
    public final ImmutableList<T> all() {
        return ImmutableList.copyOf(outerObjects);
    }

    /**
     * Obtains an immutable list with outer objects of messages belonging to the passed tenant.
     */
    public final ImmutableList<T> ofTenant(TenantId tenantId) {
        checkNotNull(tenantId);
        var result = outerObjects.stream()
                .filter(m -> tenantId.equals(m.tenant()))
                .collect(toImmutableList());
        return result;
    }
}
