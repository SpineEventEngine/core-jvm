/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate;

import java.io.Serial;

import static java.lang.String.format;

/**
 * Thrown when the history read for an aggregate is provably incomplete — the storage
 * returned fewer events than the aggregate's authoritative version implies.
 *
 * <p>This normally happens with an eventually-consistent storage (such as Google Cloud
 * Datastore): an event already durably written to the aggregate history may be
 * temporarily unavailable during a later {@linkplain AggregateStorage#read(Object, int)
 * backward read}. Reconstructing the aggregate from such a partial history would corrupt
 * its state and, once the snapshot trigger is reached, persist a corrupted snapshot that
 * poisons all future loads.
 *
 * <p>To prevent this, {@link AggregateStorage} reconciles the number of events read
 * against the aggregate's {@linkplain AggregateStorage#writeState(Aggregate) stored state
 * version}. Unlike the query-based history read, the stored state is looked up by key and
 * is therefore strongly consistent on such stores. On a shortfall, the load is refused by
 * throwing this exception rather than proceeding on partial state.
 *
 * <p>The condition is normally <b>transient</b>: retrying the operation once the storage
 * reaches consistency reads the full history and succeeds. The framework does not retry
 * automatically by default. Install an {@link IncompleteHistoryRetryMonitor} to have the
 * affected signal re-delivered after a back-off delay, or handle the failure with a custom
 * {@link io.spine.server.delivery.DeliveryMonitor DeliveryMonitor} — for example, by
 * {@linkplain io.spine.server.delivery.FailedReception#repeatDispatching() re-dispatching the
 * signal immediately}.
 *
 * @see HistoryCompleteness
 * @see IncompleteHistoryRetryMonitor
 */
public final class IncompleteHistoryException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 0L;

    /**
     * Creates a new exception.
     *
     * @param aggregateId
     *         the string form of the identifier of the aggregate being loaded
     * @param authoritativeVersion
     *         the aggregate version according to its stored state
     * @param baseVersion
     *         the version the read history starts from (a snapshot version, or {@code 0}
     *         when the history is read from the beginning)
     * @param eventsRead
     *         the number of events actually returned by the storage
     */
    IncompleteHistoryException(String aggregateId,
                               int authoritativeVersion,
                               int baseVersion,
                               int eventsRead) {
        super(format(
                "The history read for aggregate `%s` is incomplete: expected %d event(s) " +
                        "after version %d (the authoritative aggregate version is %d), " +
                        "but only %d were read from the storage. This is typically caused " +
                        "by the eventual consistency of the storage; the operation should " +
                        "be retried once the storage reaches consistency.",
                aggregateId, authoritativeVersion - baseVersion, baseVersion,
                authoritativeVersion, eventsRead));
    }
}
