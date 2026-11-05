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

package io.spine.system.server

import io.kotest.matchers.collections.shouldHaveSize
import io.spine.server.BoundedContext
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DefaultSystemWriteSide` posting in parallel should")
internal class DefaultSystemWriteSideParallelSpec {

    @Test
    fun `post a diagnostic event in the task of the posting executor`() {
        ModelTests.dropAllModels()
        val builder = BoundedContext.singleTenant("Parallel Diagnostics")
        builder.systemSettings()
            .enableParallelPosting()
            .useCustomPostingExecutor { it.run() }
        builder.build().use { context ->
            val monitor = DiagnosticMonitor()
            context.internalAccess().registerEventDispatcher(monitor)

            context.systemClient().writeSide().postEvent(RoutingFailed.getDefaultInstance())

            monitor.routingFailures() shouldHaveSize 1
        }
    }
}
