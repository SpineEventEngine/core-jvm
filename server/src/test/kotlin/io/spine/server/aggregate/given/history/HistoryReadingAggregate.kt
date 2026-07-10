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
import io.spine.server.aggregate.Aggregate
import io.spine.server.command.Assign
import io.spine.test.aggregate.AggProject
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.command.AggAddTask
import io.spine.test.aggregate.command.AggCreateProject
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.test.aggregate.event.AggTaskAdded
import io.spine.test.aggregate.event.aggProjectCreated
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
     * Reads the state this aggregate had at the given time.
     */
    fun readStateAt(time: Timestamp): Optional<AggProject> = stateAt(time)

    /**
     * Reads up to [depth] most recent states of this aggregate, newest first.
     */
    fun readStatesBackward(depth: Int): Iterator<AggProject> = stateHistoryBackward(depth)
}
