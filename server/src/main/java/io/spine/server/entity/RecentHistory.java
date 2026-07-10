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

package io.spine.server.entity;

import io.spine.core.Event;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The recent history of events of a {@link TransactionalEntity}.
 *
 * <p>The history is read from the durable journal of the entity via the loader
 * {@linkplain TransactionalEntity#setRecentHistoryLoader(RecentHistoryLoader) installed}
 * by the repository managing the entity. The events are not cached on the entity side:
 * an entity serves its signals and leaves the memory; caching, if any, belongs to
 * the storage side.
 *
 * <p>An entity created outside a repository has no journal, so the reads return
 * no events.
 */
public final class RecentHistory {

    /**
     * If set, serves the {@linkplain #read(int) reads} from the durable journal
     * of the entity.
     */
    private @Nullable RecentHistoryLoader loader;

    /**
     * Creates a new instance.
     */
    RecentHistory() {
        super();
    }

    /**
     * Installs the loader serving the {@linkplain #read(int) reads} from the durable
     * journal of the entity.
     */
    void useLoader(RecentHistoryLoader loader) {
        this.loader = checkNotNull(loader);
    }

    /**
     * Reads up to {@code depth} most recent events, newest first.
     *
     * <p>The events come from the durable journal of the entity. If no loader is
     * {@linkplain #useLoader(RecentHistoryLoader) installed} — the entity was created
     * outside a repository and thus has no journal — no events are returned.
     *
     * @param depth
     *         the maximum number of the most recent events to read
     * @return an iterator over the events, newest first
     * @throws IllegalArgumentException
     *         if the {@code depth} is not positive
     */
    public Iterator<Event> read(int depth) {
        checkArgument(depth > 0, "History depth must be positive. Got %s.", depth);
        if (loader == null) {
            return Collections.emptyIterator();
        }
        return loader.load(depth);
    }
}
