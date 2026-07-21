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

package io.spine.server.entity

import io.spine.annotation.Internal
import io.spine.core.Event
import io.spine.core.Version

/**
 * Lazily loads up to a requested number of an entity's most recent journal
 * events, newest first.
 *
 * A repository installs a loader on each entity it creates or loads — via
 * [SignalDispatchingEntity.setEventHistoryLoader] — so that the
 * [recent event history reads][RecentEventHistory.read] are served from the
 * durable journal of the entity. See
 * `io.spine.server.aggregate.AggregateRepository` for the wiring on
 * the aggregate side.
 */
@Internal
public fun interface EventHistoryLoader : HistoryLoader<Event> {

    /**
     * Loads up to [depth] most recent events of the entity's journal, newest first.
     *
     * When [startingFrom] is given, only the events with versions strictly
     * lower than it are loaded. `null` starts from the newest event.
     *
     * @param depth The maximum number of the most recent events to load; positive.
     * @param startingFrom If set, only the events with versions lower than this one are loaded.
     * @return An iterator over the loaded events, newest first.
     */
    override fun load(depth: Int, startingFrom: Version?): Iterator<Event>
}
