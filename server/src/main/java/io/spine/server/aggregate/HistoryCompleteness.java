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

import io.spine.base.Identifier;
import io.spine.core.Version;
import org.jspecify.annotations.Nullable;

/**
 * Verifies that an {@link AggregateHistory} read back from the storage is complete.
 *
 * <p>Guards against an eventually-consistent storage returning fewer events than actually
 * exist for an aggregate — see {@link IncompleteHistoryException} for the failure mode
 * this prevents.
 *
 * <p>The number of events in the read history is reconciled against the number implied by
 * the aggregate's authoritative version:
 * <pre>{@code
 *     expected = authoritativeVersion - baseVersion
 * }</pre>
 * where {@code baseVersion} is the version of the {@linkplain AggregateHistory#getSnapshot()
 * snapshot} that starts the history, or {@code 0} when the history is read from the
 * beginning. Because aggregate event versions are strictly contiguous — each applied event
 * increments the version by exactly one (see {@link VersionSequence}) — fewer events than
 * {@code expected} proves that one or more events (at the tail, in the middle, or at the
 * head) were not returned by the read.
 */
final class HistoryCompleteness {

    /**
     * Prevents instantiation of this utility class.
     */
    private HistoryCompleteness() {
    }

    /**
     * Ensures the read history contains every event the aggregate is known to have.
     *
     * <p>When {@code authoritativeVersion} is {@code null} the check is skipped: without an
     * independently-consistent version there is nothing reliable to reconcile against (for
     * example, legacy data written before the aggregate state was persisted). A history
     * with <i>more</i> events than expected is also accepted, so that a stored state
     * version lagging behind the events (e.g. after a partial write) never rejects an
     * otherwise-complete history.
     *
     * @param id
     *         the aggregate identifier, used for diagnostics
     * @param history
     *         the history read from the storage, or {@code null} if the aggregate has no
     *         stored history
     * @param authoritativeVersion
     *         the strongly-consistent current version of the aggregate as recorded in its
     *         stored state, or {@code null} if unknown
     * @param <I>
     *         the type of the aggregate identifier
     * @throws IncompleteHistoryException
     *         if the history is provably incomplete
     */
    static <I> void check(I id,
                          @Nullable AggregateHistory history,
                          @Nullable Version authoritativeVersion) {
        if (authoritativeVersion == null) {
            return;
        }
        var authoritative = authoritativeVersion.getNumber();
        var baseVersion = history != null && history.hasSnapshot()
                          ? history.getSnapshot()
                                   .getVersion()
                                   .getNumber()
                          : 0;
        var eventsRead = history == null ? 0 : history.getEventCount();
        // The stored state version may lag the events (e.g. after a partial write) and can
        // even trail the snapshot the read starts from; clamp so the expected count is never
        // negative and such a lagging witness is simply treated as "nothing to reconcile".
        var expected = Math.max(0, authoritative - baseVersion);
        if (eventsRead < expected) {
            throw new IncompleteHistoryException(
                    Identifier.toString(id), authoritative, baseVersion, eventsRead);
        }
    }
}
