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

package io.spine.server.event.given.bus;

import io.spine.core.Ack;
import io.spine.core.ErrorCase;
import io.spine.server.bus.BusFilter;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.test.event.EBTaskAdded;
import io.spine.test.event.Task;

import java.util.Optional;

/**
 * Filters out the {@link EBTaskAdded} events that have their {@link Task#getDone()}
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
                return reject(envelope, ErrorCase.INVALID_REQUEST);
            }
        }
        return letPass();
    }
}
