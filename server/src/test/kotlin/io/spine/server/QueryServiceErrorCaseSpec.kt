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

package io.spine.server

import io.grpc.StatusRuntimeException
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.client.QueryResponse
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.given.diagnostics.RequestDiagnosticsMonitor
import io.spine.system.server.RequestValidationFailed
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`QueryService` should report errors with `ErrorCase`, and")
internal class QueryServiceErrorCaseSpec {

    private lateinit var context: BoundedContext
    private lateinit var service: QueryService
    private lateinit var observer: MemoizingObserver<QueryResponse>

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = BoundedContext.singleTenant("Projects").build()
        context.register(Given.ProjectDetailsRepository())
        service = QueryService.withSingle(context)
        observer = memoizingObserver()
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    fun `fail an invalid query with 'INVALID_REQUEST', keeping the details in the context`() {
        val monitor = RequestDiagnosticsMonitor.of(context)
        val query = Given.AQuery.readAllProjects()
        val withLimitButNoOrder = query.toBuilder()
            .setFormat(query.format.toBuilder().setLimit(1))
            .build()

        service.read(withLimitButNoOrder, observer)

        val error = observer.error.shouldBeInstanceOf<StatusRuntimeException>()
        StreamObservers.errorCaseOf(error).orElse(null) shouldBe INVALID_REQUEST
        val diagnostics = monitor.diagnostics
        diagnostics shouldHaveSize 1
        val event = diagnostics[0].shouldBeInstanceOf<RequestValidationFailed>()
        event.request.typeUrl shouldBe query.target.type
    }
}
