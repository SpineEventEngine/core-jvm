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

import io.spine.base.CommandMessage
import io.spine.core.Command
import io.spine.core.Event
import io.spine.test.procman.ProjectId
import io.spine.test.procman.command.pmAddTask
import io.spine.test.procman.command.pmCompleteProject
import io.spine.test.procman.command.pmCreateProject
import io.spine.test.procman.command.pmReviewBacklog
import io.spine.test.procman.command.pmStartProject
import io.spine.test.procman.command.pmThrowEntityAlreadyArchived
import io.spine.test.procman.event.pmOwnerChanged
import io.spine.testdata.Sample
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.server.TestEventFactory

/**
 * Signal factories for the process-manager journaling and double-dispatch specs.
 */
internal object JournalTestEnv {

    private val eventFactory = TestEventFactory.newInstance(JournalTestEnv::class.java)
    private val requestFactory = TestActorRequestFactory(JournalTestEnv::class.java)

    fun newProjectId(): ProjectId = Sample.messageOfType(ProjectId::class.java)

    fun createProject(id: ProjectId): Command =
        command(pmCreateProject { projectId = id })

    fun addTask(id: ProjectId): Command =
        command(pmAddTask { projectId = id })

    fun startProject(id: ProjectId): Command =
        command(pmStartProject { projectId = id })

    fun completeProject(id: ProjectId): Command =
        command(pmCompleteProject { projectId = id })

    fun reviewBacklog(id: ProjectId): Command =
        command(pmReviewBacklog { projectId = id })

    fun throwEntityAlreadyArchived(id: ProjectId): Command =
        command(pmThrowEntityAlreadyArchived { projectId = id })

    fun ownerChanged(id: ProjectId): Event =
        eventFactory.createEvent(pmOwnerChanged { projectId = id })

    private fun command(message: CommandMessage): Command =
        requestFactory.command().create(message)
}
