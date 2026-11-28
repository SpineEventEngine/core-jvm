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

package io.spine.server.given

import io.spine.core.External
import io.spine.core.Subscribe
import io.spine.server.command.AbstractAssignee
import io.spine.server.command.Assign
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.AbstractEventSubscriber
import io.spine.server.event.React
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.test.aggregate.projectId
import io.spine.test.aggregate.rejection.AggCannotStartArchivedProject
import io.spine.test.subscriptionservice.ReportId
import io.spine.test.subscriptionservice.command.SendReport
import io.spine.test.subscriptionservice.event.ReportSent
import io.spine.test.subscriptionservice.event.reportSent

/**
 * Declares [ReportSent] in the signature of its receptor, so that its context declares
 * the event type.
 */
internal class ReportOnProjectCreated : AbstractEventReactor() {

    @React
    internal fun on(@Suppress("UNUSED_PARAMETER") event: AggProjectCreated): ReportSent =
        reportSent { id = ReportId.generate() }
}

/**
 * Remembers the [ReportSent] events that reach its context from other contexts.
 *
 * Subscribing to the external events makes the broker deliver the reports that other contexts
 * post to the event bus of the context of this subscriber.
 */
internal class ExternalReports : AbstractEventSubscriber() {

    private val received = mutableListOf<ReportSent>()

    /**
     * The reports that came from other contexts, in the order of receiving.
     */
    val reports: List<ReportSent>
        get() = received.toList()

    @Subscribe
    internal fun on(@External event: ReportSent) {
        received.add(event)
    }
}

/**
 * Rejects every [SendReport] command with [AggCannotStartArchivedProject], which its
 * receptor does not declare.
 *
 * Kotlin does not require declaring the thrown exceptions, so the context of this assignee
 * posts the rejection while declaring nothing about it.
 */
internal class UndeclaredRejector : AbstractAssignee() {

    @Assign
    internal fun on(command: SendReport): ReportSent =
        throw AggCannotStartArchivedProject.newBuilder()
            .setProjectId(projectOf(command.id))
            .build()

    internal companion object {

        /**
         * Obtains the ID of the project named in the rejection of the given report.
         */
        fun projectOf(report: ReportId): ProjectId =
            projectId { uuid = report.uuid }
    }
}
