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

import io.grpc.ServerBuilder
import io.spine.logging.WithLogging
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The keepalive that [GrpcContainer] sets for the gRPC server it exposes at a port.
 *
 * The server pings the client of a connection that has no read activity for [TIME_MINUTES],
 * and closes the connection if the client does not answer within [TIMEOUT_SECONDS].
 * The calls of a client that disappears without closing its connection end then.
 */
internal object KeepAlive : WithLogging {

    /**
     * The minutes without read activity on a connection after which the server pings
     * the client.
     */
    const val TIME_MINUTES: Long = 1

    /**
     * The seconds that the server waits for the answer to a ping before it closes
     * the connection.
     */
    const val TIMEOUT_SECONDS: Long = 20

    /**
     * Sets the keepalive of the given builder.
     *
     * Leaves the builder as is if its transport does not support keepalive.
     *
     * @return The given builder.
     */
    @JvmStatic
    fun applyTo(builder: ServerBuilder<*>): ServerBuilder<*> {
        try {
            builder.keepAliveTime(TIME_MINUTES, MINUTES)
                .keepAliveTimeout(TIMEOUT_SECONDS, SECONDS)
        } catch (e: UnsupportedOperationException) {
            logger.atInfo().withCause(e).log {
                "The transport of the gRPC server does not support keepalive." +
                        " A client that disappears without closing its connection" +
                        " is noticed only after a long delay."
            }
        }
        return builder
    }
}
