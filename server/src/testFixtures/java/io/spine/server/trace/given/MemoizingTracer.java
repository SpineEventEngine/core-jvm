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

package io.spine.server.trace.given;

import com.google.protobuf.Message;
import io.spine.core.MessageId;
import io.spine.core.Signal;
import io.spine.server.trace.AbstractTracer;
import io.spine.system.server.EntityTypeName;
import io.spine.type.TypeUrl;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newHashSet;
import static io.spine.base.Identifier.pack;

/**
 * An implementation of {@link io.spine.server.trace.Tracer} which memoizes the entities which
 * process the given message.
 */
public final class MemoizingTracer extends AbstractTracer {

    private final Set<MessageId> receivers = newHashSet();

    MemoizingTracer(Signal<?, ?, ?> signal) {
        super(signal);
    }

    public boolean isReceiver(Message entityId, TypeUrl entityStateType) {
        checkNotNull(entityId);
        var id = MessageId.newBuilder()
                .setId(pack(entityId))
                .setTypeUrl(entityStateType.value())
                .build();
        return receivers.contains(id);
    }

    @Override
    public void processedBy(MessageId receiver, EntityTypeName receiverType) {
        checkNotNull(receiver);
        var idWithoutVersion = receiver.toBuilder()
                .clearVersion()
                .build();
        receivers.add(idWithoutVersion);
    }

    @Override
    public void close() {
        // NOP.
    }
}
