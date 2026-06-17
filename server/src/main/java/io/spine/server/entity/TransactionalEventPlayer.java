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

package io.spine.server.entity;

import io.spine.core.Event;
import io.spine.server.dispatch.BatchDispatchOutcome;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An {@link EventPlayer} that plays events upon the given {@link Transaction}.
 */
final class TransactionalEventPlayer implements EventPlayer {

    private final EventPlayingTransaction<?, ?, ?, ?> transaction;

    /**
     * Creates a new instance of {@code TransactionalEventPlayer}.
     *
     * @param transaction the transaction
     */
    TransactionalEventPlayer(EventPlayingTransaction<?, ?, ?, ?> transaction) {
        this.transaction = checkNotNull(transaction);
    }

    /**
     * Plays the given events upon the underlying entity transaction.
     */
    @Override
    public BatchDispatchOutcome play(Iterable<Event> events) {
        checkNotNull(events);
        var process = new BatchDispatch(transaction);
        for (var event : events) {
            process.play(event);
        }
        return process.summary();
    }
}
