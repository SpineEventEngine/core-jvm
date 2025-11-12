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

package io.spine.server.event.given.bus;

import io.spine.base.Error;
import io.spine.core.Ack;
import io.spine.server.bus.BusFilter;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.test.event.EBTaskAdded;
import io.spine.test.event.Task;

import java.util.Optional;

/**
 * Filters out the {@link EBTaskAdded} events which have their {@link Task#getDone()}
 * property set to {@code true}.
 */
public final class TaskCreatedFilter implements BusFilter<EventEnvelope> {

    private static final EventClass TASK_ADDED_CLASS = EventClass.from(EBTaskAdded.class);

    @Override
    public Optional<Ack> filter(EventEnvelope envelope) {
        if (TASK_ADDED_CLASS.equals(envelope.messageClass())) {
            var message = (EBTaskAdded) envelope.message();
            var task = message.getTask();
            if (task.getDone()) {
                var error = error();
                return reject(envelope, error);
            }
        }
        return letPass();
    }

    private static Error error() {
        return Error.newBuilder()
                    .setMessage("The task cannot be created in a 'completed' state.")
                    .build();
    }
}
