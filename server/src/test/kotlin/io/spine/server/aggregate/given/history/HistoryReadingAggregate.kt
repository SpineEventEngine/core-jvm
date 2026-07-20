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

package io.spine.server.aggregate.given.history

import com.google.protobuf.Timestamp
import io.spine.core.Event
import io.spine.server.aggregate.Aggregate
import io.spine.server.command.Assign
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.test.aggregate.AggProject
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.Status
import io.spine.test.aggregate.command.AggAddTask
import io.spine.test.aggregate.command.AggCreateProject
import io.spine.test.aggregate.command.AggPauseProject
import io.spine.test.aggregate.command.AggStartProject
import io.spine.test.aggregate.event.AggProjectArchived
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.test.aggregate.event.AggProjectPaused
import io.spine.test.aggregate.event.AggProjectStarted
import io.spine.test.aggregate.event.AggTaskAdded
import io.spine.test.aggregate.event.aggProjectCreated
import io.spine.test.aggregate.event.aggProjectStarted
import io.spine.test.aggregate.event.aggTaskAdded
import java.util.Optional

/**
 * A test aggregate opening the `protected` state history reads of `Aggregate`
 * to Kotlin tests.
 *
 * Each handled command mutates the state, so consecutive dispatches produce
 * distinct state records.
 */
internal class HistoryReadingAggregate(id: ProjectId) :
    Aggregate<ProjectId, AggProject, AggProject.Builder>(id) {

    @Assign
    fun handle(command: AggCreateProject): AggProjectCreated {
        alter {
            id = command.projectId
        }
        return aggProjectCreated {
            projectId = command.projectId
        }
    }

    @Assign
    fun handle(command: AggAddTask): AggTaskAdded {
        alter {
            addTask(command.task)
        }
        return aggTaskAdded {
            projectId = command.projectId
            task = command.task
        }
    }

    /**
     * Always fails, serving the failed-dispatch cases of the specs.
     */
    @Assign
    fun handle(command: AggPauseProject): AggProjectPaused {
        error("Pausing the project `${command.projectId.uuid}` always fails.")
    }

    /**
     * Archives the project without emitting events or touching the state:
     * a lifecycle-only dispatch, which must still advance the version.
     */
    @React
    fun on(@Suppress("UNUSED_PARAMETER") event: AggProjectArchived): NoReaction {
        setArchived(true)
        return noReaction()
    }

    @Assign
    fun handle(command: AggStartProject): AggProjectStarted {
        check(state().status != Status.STARTED) {
            "The project is already started."
        }
        statesSeenOnStart = stateHistoryBackward(10)
            .asSequence()
            .toList()
        eventsSeenOnStart = eventHistoryBackward(10)
            .asSequence()
            .toList()
        alter {
            status = Status.STARTED
        }
        return aggProjectStarted {
            projectId = command.projectId
        }
    }

    /**
     * Reads the state this aggregate had at the given time.
     */
    fun readStateAt(time: Timestamp): Optional<AggProject> = stateAt(time)

    /**
     * Reads up to [depth] most recent states of this aggregate, newest first.
     */
    fun readStatesBackward(depth: Int): Iterator<AggProject> = stateHistoryBackward(depth)

    /**
     * Reads up to [depth] most recent events of this aggregate, newest first.
     */
    fun readEventsBackward(depth: Int): Iterator<Event> = eventHistoryBackward(depth)

    internal companion object {

        /**
         * The recorded states the [AggStartProject] receptor saw during
         * the dispatch, newest first.
         *
         * Captured statically: the instance which handles the command is not
         * the one a test can obtain from the repository afterwards.
         */
        var statesSeenOnStart: List<AggProject> = emptyList()

        /**
         * The recent events the [AggStartProject] receptor saw during
         * the dispatch, newest first.
         *
         * Captured statically for the same reason as [statesSeenOnStart].
         */
        var eventsSeenOnStart: List<Event> = emptyList()
    }
}
