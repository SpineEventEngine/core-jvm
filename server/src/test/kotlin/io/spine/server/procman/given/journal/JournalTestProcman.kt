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

package io.spine.server.procman.given.journal

import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.server.command.Assign
import io.spine.server.command.Command
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.rejection.EntityAlreadyArchived
import io.spine.server.event.React
import io.spine.server.procman.ProcessManager
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.test.procman.ElephantProcess
import io.spine.test.procman.ProjectId
import io.spine.test.procman.command.PmAddTask
import io.spine.test.procman.command.PmCompleteProject
import io.spine.test.procman.command.PmCreateProject
import io.spine.test.procman.command.PmReviewBacklog
import io.spine.test.procman.command.PmStartProject
import io.spine.test.procman.command.PmThrowEntityAlreadyArchived
import io.spine.test.procman.command.pmAddTask
import io.spine.test.procman.event.PmNothingDone
import io.spine.test.procman.event.PmNotificationSent
import io.spine.test.procman.event.PmOwnerChanged
import io.spine.test.procman.event.PmProjectCreated
import io.spine.test.procman.event.PmProjectStarted
import io.spine.test.procman.event.PmTaskAdded
import io.spine.test.procman.event.pmNothingDone
import io.spine.test.procman.event.pmNotificationSent
import io.spine.test.procman.event.pmProjectCreated
import io.spine.test.procman.event.pmProjectStarted
import io.spine.test.procman.event.pmTaskAdded

/**
 * A test process manager opening the `protected` event history reads and
 * the duplicate detection of [ProcessManager] to Kotlin tests.
 *
 * Only the [PmCreateProject] handler mutates the state: the other receptors
 * emit events while leaving the state unchanged, so the specs can tell the
 * stored-because-changed dispatches from the stored-because-emitted ones.
 */
@Suppress("TooManyFunctions") // The fixture exposes many `protected` members to the specs.
internal class JournalTestProcman :
    ProcessManager<ProjectId, ElephantProcess, ElephantProcess.Builder>() {

    @Assign
    fun handle(command: PmCreateProject): PmProjectCreated {
        alter {
            id = command.projectId
        }
        return pmProjectCreated {
            projectId = command.projectId
        }
    }

    /**
     * Emits an event without touching the state, so the dispatch is stored —
     * and journaled — only because of the emitted event.
     */
    @Assign
    fun handle(command: PmAddTask): PmTaskAdded =
        pmTaskAdded {
            projectId = command.projectId
        }

    @Assign
    fun handle(command: PmStartProject): PmProjectStarted =
        pmProjectStarted {
            projectId = command.projectId
        }

    /**
     * Reads the recent event history while handling, serving the cases which
     * verify the reads made by the dispatching instance itself.
     */
    @Assign
    fun handle(command: PmCompleteProject): PmNothingDone {
        eventHistoryBackward(1).hasNext()
        return pmNothingDone {
            projectId = command.projectId
        }
    }

    /**
     * Always rejects, serving the rejections-are-not-journaled cases.
     */
    @Assign
    @Throws(EntityAlreadyArchived::class)
    fun handle(command: PmThrowEntityAlreadyArchived): PmProjectCreated {
        throw EntityAlreadyArchived.newBuilder()
            .setEntityId(Identifier.pack(command.projectId))
            .build()
    }

    /**
     * Substitutes the command with another one, serving the
     * commands-are-not-journaled cases.
     */
    @Command
    fun transform(command: PmReviewBacklog): PmAddTask =
        pmAddTask {
            projectId = command.projectId
        }

    @React
    fun on(event: PmOwnerChanged): PmNotificationSent =
        pmNotificationSent {
            projectId = event.projectId
        }

    /**
     * Reads up to [depth] most recent events of this process manager, newest first.
     */
    fun readEventsBackward(depth: Int): Iterator<Event> = eventHistoryBackward(depth)

    /**
     * Tells whether the [depth] most recent events of this process manager contain
     * an event that satisfies the [predicate].
     */
    fun containsEvent(depth: Int, predicate: (Event) -> Boolean): Boolean =
        eventHistoryContains(depth) { predicate(it) }

    /**
     * Exposes the duplicate detection for the passed command to the tests.
     */
    fun checkDuplicate(command: CommandEnvelope): DispatchOutcome? = detectDuplicate(command)

    /**
     * Exposes the duplicate detection for the passed event to the tests.
     */
    fun checkDuplicate(event: EventEnvelope): DispatchOutcome? = detectDuplicate(event)
}
