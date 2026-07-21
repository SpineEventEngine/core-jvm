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
import io.spine.core.Version

/**
 * Lazily loads up to a requested number of the most recent items of
 * an entity's durable history, newest first.
 *
 * A repository installs a loader on each entity it creates or loads, so
 * that the [recent history reads][RecentHistory.read] are served from
 * the durable storage of the entity without eagerly reading it on every
 * load.
 *
 * @param R The type of the loaded items.
 * @see EventHistoryLoader
 * @see StateHistoryLoader
 */
@Internal
public interface HistoryLoader<R : Any> {

    /**
     * Loads up to [depth] most recent items of the entity's history,
     * newest first.
     *
     * When [startingFrom] is given, only the items with versions strictly
     * lower than it are loaded — so a read may continue below the items
     * it has already obtained. `null` starts from the newest item.
     *
     * @param depth The maximum number of the most recent items to load; positive.
     * @param startingFrom If set, only the items with versions lower than this one are loaded.
     * @return An iterator over the loaded items, newest first.
     */
    public fun load(depth: Int, startingFrom: Version?): Iterator<R>
}
