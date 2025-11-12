/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract base for builders of objects that depend on or expose server-side gRPC objects.
 *
 * <p>A gRPC server can be exposes at a given port or in-process with a given name. In-process
 * arrangements, while being fully-featured are used primarily for testing.
 *
 * @see io.grpc.inprocess.InProcessChannelBuilder
 * @see io.grpc.inprocess.InProcessServerBuilder
 */
public abstract class ConnectionBuilder {

    private final @Nullable Integer port;
    private final @Nullable String serverName;

    ConnectionBuilder(@Nullable Integer port, @Nullable String serverName) {
        if (port != null) {
            checkArgument(serverName == null,
                          "`serverName` must be `null` if `port` is defined.");
            this.port = port;
            this.serverName = null;
        } else {
            checkArgument(serverName != null,
                          "Either `port` or `serverName` must be defined.");
            this.serverName = serverName;
            this.port = null;
        }
    }

    /**
     * Checks whether the port has been configured.
     *
     * @return {@code true} if the port was set, {@code false} otherwise
     */
    public final boolean hasPort() {
        return port != null;
    }

    /**
     * Obtains the port of the connection.
     *
     * @return the port
     * @throws NullPointerException if the port was not set (in-process connection)
     */
    public final int getPort() {
        checkNotNull(port);
        return port;
    }

    /**
     * Obtains the port of the connection, or empty {@code Optional} for in-process connection.
     *
     * @deprecated Use {@link #getPort()} and {@link #hasPort()} instead.
     * @see #serverName()
     */
    @Deprecated
    public final Optional<Integer> port() {
        return Optional.ofNullable(port);
    }

    /**
     * Checks whether the server name has been configured.
     *
     * @return {@code true} if the server name was set, {@code false} otherwise
     */
    public final boolean hasServerName() {
        return serverName != null;
    }

    /**
     * Obtains the name of the in-process connection.
     *
     * @return the server name
     * @throws NullPointerException if the server name was not set (port-based connection)
     */
    public final String getServerName() {
        return serverName;
    }

    /**
     * Obtains the name of the in-process connection, or empty {@code Optional} if the connection
     * is made via a port.
     *
     * @deprecated Use {@link #getServerName()} and {@link #hasServerName()} instead.
     * @see #port()
     */
    @Deprecated
    public final Optional<String> serverName() {
        return Optional.ofNullable(serverName);
    }
}
