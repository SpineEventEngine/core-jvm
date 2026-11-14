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

import io.spine.core.Ack
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.testing.server.model.ModelTests
import io.spine.testing.server.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * The abstract base for test suites checking how [CommandService] work
 * with single- and multi-tenant contexts.
 */
abstract class CommandServiceTenancyTest {

    protected lateinit var application: Application
    protected lateinit var service: CommandService
    protected lateinit var responseObserver: MemoizingObserver<Ack>

    /**
     * Creates the builder of the only context of the application under the test.
     */
    protected abstract fun contextBuilder(): BoundedContextBuilder

    @BeforeEach
    fun setup() {
        ModelTests.dropAllModels()
        responseObserver = memoizingObserver()
        application = testApplication {
            add(contextBuilder())
        }
        service = CommandService.of(application)
    }

    @AfterEach
    fun closeApplication() {
        application.close()
    }
}
