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

package io.spine.client

import io.grpc.inprocess.InProcessServerBuilder
import io.spine.server.Application
import io.spine.server.BoundedContextBuilder
import io.spine.server.Server
import io.spine.testing.server.testApplication
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Abstract base for Client API tests.
 *
 * Both `Client` and `Server` are created in-process with a name generated
 * by [.generateServerName]. In order to configure the `Server` derived test suites
 * must implement the [.contexts] providing builders of contexts of the backend services.
 *
 * The client and the server are shut down after each test, and then their application
 * is closed.
 */
internal abstract class AbstractClientTest {

    private lateinit var application: Application
    private lateinit var server: Server
    private lateinit var client: Client

    /**
     * Generates the name for the in-process server and the client.
     *
     *
     * Derived test suites may override to provide a custom name.
     */
    protected fun generateServerName(): String =
        InProcessServerBuilder.generateName()

    /**
     * Provides builders of the contexts exposed by the in-process server used in the tests.
     */
    protected abstract fun contexts(): List<BoundedContextBuilder>

    /**
     * Builds an application of the contexts, and then the server that exposes them.
     */
    @BeforeEach
    @Throws(IOException::class)
    fun createServerAndClient() {
        val serverName = generateServerName()
        application = testApplication {
            contexts().forEach { add(it) }
        }
        server = Server.inProcess(serverName)
            .setApplication(application)
            .build()
        server.start()

        client = newClientBuilder(serverName).build()
    }

    protected open fun newClientBuilder(serverName: String): Client.Builder {
        return Client.inProcess(serverName) // When shutting down,
            // terminate the client immediately since all the requests made in tests
            // are going to be complete by that time.
            .shutdownTimeout(0, TimeUnit.SECONDS)
    }

    /**
     * Shuts down the client and the server, and then closes the application they work with.
     */
    @AfterEach
    fun shutdownAndClose() {
        client.shutdown()
        server.shutdown()
        application.close()
    }

    /** Obtains the reference to the client.  */
    protected fun client(): Client {
        return client
    }
}
