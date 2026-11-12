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

import com.google.protobuf.Empty
import com.google.protobuf.Message
import com.google.protobuf.StringValue
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.StreamObserver
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.base.EventMessage
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.TenantId
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.app.RefusedRequest
import io.spine.server.given.errorCase
import io.spine.system.server.event.QueryExecutionFailed
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.testApplication
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ServiceDelegate` should")
internal class ServiceDelegateSpec {

    private lateinit var application: Application

    @BeforeEach
    fun createApplication() {
        application = testApplication { add(BoundedContextBuilder.assumingTests()) }
    }

    @AfterEach
    fun closeApplication() {
        application.close()
    }

    @Test
    @MuteLogging
    fun `answer a rejected request even if the rejection cannot be recorded`() {
        val observer = memoizingObserver<Empty>()

        RejectingDelegate(application).serve(StringValue.of("A request"), observer, null)

        observer.error.shouldNotBeNull().errorCase() shouldBe INVALID_REQUEST
    }
}

/**
 * Rejects every request in the only context of the given application.
 *
 * The tenant of a request is obtained once, for the check of the tenant. Then it cannot be
 * obtained, so the rejection cannot be recorded.
 */
private class RejectingDelegate(application: Application) : ServiceDelegate<StringValue, Empty>(
    NoService,
    TypeDictionary.newBuilder()
        .putAll(application.contexts().single()) { setOf(TypeUrl.of(StringValue::class.java)) }
        .build(),
    application
) {

    private var tenantObtained = false

    override fun enclosedMessageType(request: StringValue): TypeUrl =
        TypeUrl.of(StringValue::class.java)

    override fun tenantOf(request: StringValue): TenantId {
        check(!tenantObtained) { "The tenant cannot be obtained." }
        tenantObtained = true
        return TenantId.getDefaultInstance()
    }

    override fun refusedRequest(request: StringValue): RefusedRequest =
        RefusedRequest.getDefaultInstance()

    override fun idOf(request: StringValue): Message = request

    override fun tenantNotSpecified(request: StringValue): EventMessage? = null

    override fun tenantNotExpected(request: StringValue, tenant: TenantId): EventMessage? = null

    override fun failureEvent(request: StringValue, error: Error): EventMessage =
        QueryExecutionFailed.getDefaultInstance()

    override fun serve(
        context: BoundedContext,
        request: StringValue,
        observer: StreamObserver<Empty>,
        params: Any?
    ) {
        val event = QueryExecutionFailed.getDefaultInstance()
        reject(context, request, event, INVALID_REQUEST, observer)
    }

    override fun serveNoContext(
        request: StringValue,
        observer: StreamObserver<Empty>,
        params: Any?
    ) {
        refuseNotServed(request, observer)
    }
}

/**
 * A service that serves nothing.
 */
private object NoService : BindableService {

    override fun bindService(): ServerServiceDefinition =
        ServerServiceDefinition.builder("NoService").build()
}
