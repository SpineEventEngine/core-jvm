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

package io.spine.server.event;

import io.spine.annotation.Internal;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.TransactionalEntity;
import io.spine.server.type.EventEnvelope;

import java.util.function.BiFunction;

/**
 * A dispatch of the event to the appropriate handler of an entity.
 *
 * <p>Unlike the {@link io.spine.server.command.DispatchCommand}, this class is a simple wrapper
 * around the {@link BiFunction} that performs an actual event dispatch.
 *
 * @param <I>
 *         the type of entity ID
 * @param <E>
 *         the type of entity
 */
@Internal
public final class EventDispatch<I, E extends TransactionalEntity<I, ?, ?>> {

    private final BiFunction<E, EventEnvelope, DispatchOutcome> dispatchFunction;
    private final E entity;
    private final EventEnvelope event;

    /**
     * Creates a new {@code EventDispatch} from the given dispatch function.
     *
     * @param dispatchFunction
     *         the {@code BiFunction} that performs a dispatch operation
     * @param entity
     *         the entity to which the event is dispatched
     * @param event
     *         the dispatched event
     */
    public EventDispatch(BiFunction<E, EventEnvelope, DispatchOutcome> dispatchFunction,
                         E entity,
                         EventEnvelope event) {
        this.dispatchFunction = dispatchFunction;
        this.entity = entity;
        this.event = event;
    }

    /**
     * Executes the dispatch operation, returning its result.
     */
    public DispatchOutcome perform() {
        return dispatchFunction.apply(entity, event);
    }

    /**
     * Returns the entity to which the event is dispatched.
     */
    public E entity() {
        return entity;
    }

    /**
     * Returns the dispatched event.
     */
    public EventEnvelope event() {
        return event;
    }
}
