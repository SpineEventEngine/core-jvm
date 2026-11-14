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

import io.grpc.Status.Code.INTERNAL
import io.grpc.Status.Code.INVALID_ARGUMENT
import io.grpc.StatusRuntimeException
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.client.Query
import io.spine.client.QueryResponse
import io.spine.client.ResponseFormat
import io.spine.client.given.TaskAggregate
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.tenantId
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.entity.Repository
import io.spine.server.given.RequestEventMonitor
import io.spine.server.given.errorCase
import io.spine.system.server.event.QueryExecutionFailed
import io.spine.system.server.event.QueryValidationFailed
import io.spine.system.server.event.UnsupportedQueryReceived
import io.spine.test.client.tasks.CTask
import io.spine.test.projection.Project
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.testApplication
import io.spine.type.TypeUrl
import io.spine.validation.NonValidated
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `QueryServiceTest` with the specs of what the service answers
 * to a query it does not serve, and what it records.
 */
@MuteLogging
@DisplayName("`QueryService` should")
internal class QueryServiceErrorCaseSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val monitor = RequestEventMonitor()
    private var application: Application? = null
    private lateinit var service: QueryService

    @AfterEach
    fun closeApplication() {
        application?.close()
    }

    @Nested inner class
    `reject a query, recording why, if it` {

        @Test
        fun `fails validation`() {
            serve(Given.ProjectDetailsRepository())
            val invalid = projectsQuery().toBuilder()
                .clearId()
                .buildPartial()

            read(invalid).errorCase() shouldBe INVALID_REQUEST

            val failed = monitor.single<QueryValidationFailed>()
            failed.validationError.constraintViolationList.shouldNotBeEmpty()
            failed.actorContext shouldBe invalid.context
        }

        @Test
        fun `has a limit but no ordering`() {
            serve(Given.ProjectDetailsRepository())
            val query = projectsQuery()
            val limited = query.toBuilder()
                .setFormat(ResponseFormat.newBuilder().setLimit(5))
                .build()

            read(limited).errorCase() shouldBe INVALID_REQUEST

            val failed = monitor.single<QueryValidationFailed>()
            failed.id shouldBe limited.id
            val violation = failed.validationError.constraintViolationList.single()
            violation.fieldPath.fieldNameList shouldContainExactly listOf("format", "limit")
        }

        @Test
        fun `targets a type not exposed for querying`() {
            serve(DefaultRepository.of(TaskAggregate::class.java))
            val query = requests.query().all(CTask::class.java)

            read(query).errorCase() shouldBe UNSUPPORTED_REQUEST

            val unsupported = monitor.single<UnsupportedQueryReceived>()
            unsupported.id shouldBe query.id
            unsupported.typeUrl shouldBe TypeUrl.of(CTask::class.java).value()
            unsupported.actorContext shouldBe query.context
        }
    }

    @Nested inner class
    `refuse a query, recording nothing, if its target type is` {

        @Test
        fun internal() {
            serve(Given.ProjectDetailsRepository())

            read(Given.AQuery.readInternalType()).errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `served by no context`() {
            serve(Given.ProjectDetailsRepository())

            read(Given.AQuery.readUnknownType()).errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `unknown to the server`() {
            serve(Given.ProjectDetailsRepository())

            read(targeting("type.spine.io/spine.test.NoSuchType"))
                .errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `not a type URL`() {
            serve(Given.ProjectDetailsRepository())

            read(targeting("")).errorCase() shouldBe INVALID_REQUEST

            monitor.events.shouldBeEmpty()
        }
    }

    @Test
    fun `answer a failure with the internal error, and record it`() {
        serve(Given.ThrowingProjectDetailsRepository())
        val query = projectsQuery()

        val error = read(query)

        error.shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe INTERNAL
        error.errorCase() shouldBe INTERNAL_ERROR
        val failed = monitor.single<QueryExecutionFailed>()
        failed.id shouldBe query.id
        failed.error.type shouldBe IllegalStateException::class.java.canonicalName
    }

    @Test
    fun `fail a call with a status that tells only the error case`() {
        serve(Given.ProjectDetailsRepository())

        val error = read(Given.AQuery.readUnknownType())

        error.shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe INVALID_ARGUMENT
        error.status.description.isNullOrEmpty() shouldBe true
    }

    @Test
    fun `record a problem for the tenant of the query`() {
        val application = serve(Given.ProjectDetailsRepository(), multiTenant = true)
        val tenant = tenantId { value = "Tenant of the query" }
        application.tenantIndex().keep(tenant)
        val query = TestActorRequestFactory(javaClass, tenant).query()
            .all(Project::class.java)
        val invalid = query.toBuilder()
            .clearId()
            .buildPartial()

        read(invalid).errorCase() shouldBe INVALID_REQUEST

        monitor.single<QueryValidationFailed>()
        monitor.tenantOfSingle() shouldBe tenant
    }

    /**
     * Creates the application of one context with the given repository, and the service
     * that serves its queries.
     *
     * @return The created application, which is closed after the test.
     */
    private fun serve(
        repository: Repository<*, *>,
        multiTenant: Boolean = false
    ): Application {
        val app = testApplication {
            add(
                BoundedContextBuilder.assumingTests(multiTenant)
                    .add(repository)
                    .addEventDispatcher(monitor)
            )
        }
        application = app
        service = QueryService.of(app)
        return app
    }

    private fun projectsQuery(): Query = requests.query().all(Project::class.java)

    private fun targeting(typeUrl: String): @NonValidated Query {
        val query = projectsQuery()
        return query.toBuilder()
            .setTarget(query.target.toBuilder().setType(typeUrl).buildPartial())
            .buildPartial()
    }

    /**
     * Executes the given query, expecting the call to fail.
     *
     * @return The error of the call.
     */
    private fun read(query: Query): Throwable {
        val observer = memoizingObserver<QueryResponse>()
        service.read(query, observer)
        observer.responses().shouldBeEmpty()
        return observer.error.shouldNotBeNull()
    }
}
