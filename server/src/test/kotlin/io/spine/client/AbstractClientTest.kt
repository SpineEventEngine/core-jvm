/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.client

import io.grpc.inprocess.InProcessServerBuilder
import io.spine.server.BoundedContextBuilder
import io.spine.server.Server
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Abstract base for Client API tests.
 *
 *
 * Both `Client` and `Server` are created in-process with a name generated
 * by [.generateServerName]. In order to configure the `Server` derived test suites
 * must implement the [.contexts] providing builders of contexts of the backend services.
 *
 *
 * Both client and server instances are shut down after each test.
 */
internal abstract class AbstractClientTest {

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
     * Provides builders of contexts to be added to the in-process server used in the tests.
     */
    protected abstract fun contexts(): List<BoundedContextBuilder>

    @BeforeEach
    @Throws(IOException::class)
    fun createServerAndClient() {
        val serverName = generateServerName()
        val serverBuilder = Server.inProcess(serverName)
        contexts().forEach {
            context: BoundedContextBuilder -> serverBuilder.add(context)
        }
        server = serverBuilder.build()
        server.start()

        client = newClientBuilder(serverName).build()
    }

    protected open fun newClientBuilder(serverName: String): Client.Builder {
        return Client.inProcess(serverName) // When shutting down,
            // terminate the client immediately since all the requests made in tests
            // are going to be complete by that time.
            .shutdownTimeout(0, TimeUnit.SECONDS)
    }

    @AfterEach
    fun shutdownClientAndServer() {
        client.shutdown()
        server.shutdown()
    }

    /** Obtains the reference to the client.  */
    protected fun client(): Client {
        return client
    }
}
