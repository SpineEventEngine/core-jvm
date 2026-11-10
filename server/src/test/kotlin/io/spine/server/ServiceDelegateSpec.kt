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
import io.spine.server.given.errorCase
import io.spine.system.server.event.QueryExecutionFailed
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ServiceDelegate` should")
internal class ServiceDelegateSpec {

    private lateinit var context: BoundedContext

    @BeforeEach
    fun createContext() {
        context = BoundedContextBuilder.assumingTests().build()
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    @MuteLogging
    fun `answer a rejected request even if the rejection cannot be recorded`() {
        val observer = memoizingObserver<Empty>()

        RejectingDelegate(context).serve(StringValue.of("A request"), observer, null)

        observer.error.shouldNotBeNull().errorCase() shouldBe INVALID_REQUEST
    }
}

/**
 * Rejects every request in the given context.
 *
 * The tenant of a request cannot be obtained, so the rejection cannot be recorded.
 */
private class RejectingDelegate(context: BoundedContext) : ServiceDelegate<StringValue, Empty>(
    NoService,
    TypeDictionary.newBuilder()
        .putAll(context) { setOf(TypeUrl.of(StringValue::class.java)) }
        .build()
) {

    override fun enclosedMessageType(request: StringValue): TypeUrl =
        TypeUrl.of(StringValue::class.java)

    override fun tenantOf(request: StringValue): TenantId =
        error("The tenant cannot be obtained.")

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
