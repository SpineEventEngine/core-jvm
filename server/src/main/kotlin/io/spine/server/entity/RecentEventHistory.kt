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

package io.spine.server.entity

import io.spine.core.Event
import io.spine.core.Version

/**
 * The recent history of events of a [SignalDispatchingEntity].
 *
 * The events are read from the durable journal of the entity via a loader
 * installed by the repository managing the entity and cached for the lifetime
 * of the entity instance — see [RecentHistory].
 *
 * An entity created outside a repository has no journal, so the reads
 * serve only the [appended][append] events, if any.
 */
internal class RecentEventHistory :
    RecentHistory<Event, Event, EventHistoryLoader>() {

    /**
     * Returns the event unchanged, as the stored record already is the history item.
     */
    override fun toItem(record: Event): Event = record

    override fun versionOf(record: Event): Version = record.context.version
}
