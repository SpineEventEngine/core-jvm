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
 * Signal factories for the process-manager journaling specs.
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
