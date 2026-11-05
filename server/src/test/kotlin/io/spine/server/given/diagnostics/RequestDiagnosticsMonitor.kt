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

package io.spine.server.given.diagnostics

import io.spine.core.Subscribe
import io.spine.server.BoundedContext
import io.spine.server.event.AbstractEventSubscriber
import io.spine.system.server.DiagnosticEvent
import io.spine.system.server.RequestDenied
import io.spine.system.server.RequestProcessingFailed
import io.spine.system.server.RequestValidationFailed
import io.spine.system.server.TenantCheckFailed
import io.spine.system.server.UnknownSubscriptionRequested
import io.spine.system.server.UnsupportedRequestReceived

/**
 * Remembers the diagnostic events that the System context posts for requests
 * that the server did not accept.
 */
internal class RequestDiagnosticsMonitor : AbstractEventSubscriber() {

    private val events = mutableListOf<DiagnosticEvent>()

    /**
     * The remembered events, in the order of their arrival.
     */
    val diagnostics: List<DiagnosticEvent>
        get() = events.toList()

    @Subscribe
    internal fun on(event: RequestValidationFailed) {
        events += event
    }

    @Subscribe
    internal fun on(event: UnsupportedRequestReceived) {
        events += event
    }

    @Subscribe
    internal fun on(event: TenantCheckFailed) {
        events += event
    }

    @Subscribe
    internal fun on(event: RequestDenied) {
        events += event
    }

    @Subscribe
    internal fun on(event: UnknownSubscriptionRequested) {
        events += event
    }

    @Subscribe
    internal fun on(event: RequestProcessingFailed) {
        events += event
    }

    companion object {

        /**
         * Creates a monitor that receives the diagnostic events of the given context.
         */
        fun of(context: BoundedContext): RequestDiagnosticsMonitor {
            val monitor = RequestDiagnosticsMonitor()
            context.internalAccess()
                .registerEventDispatcher(monitor)
            return monitor
        }
    }
}
