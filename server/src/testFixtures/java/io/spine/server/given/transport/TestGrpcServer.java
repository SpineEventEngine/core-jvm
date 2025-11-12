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

package io.spine.server.given.transport;

import io.grpc.Server;

import java.util.concurrent.TimeUnit;

/**
 * A test implementation of the gRPC server.
 *
 * <p>Does nothing except recording own "up"/"down" state.
 *
 * <p>As this server doesn't acquire any resources, nor does it process any calls, being
 * {@linkplain #isShutdown() shutdown} for it is the same as being
 * {@linkplain #isTerminated() terminated}.
 *
 * <p>For the same reasons all {@code awaitTermination(...)} calls are NO-OP.
 */
public final class TestGrpcServer extends Server {

    private boolean isShutdownAndTerminated;

    @Override
    public Server start() {
        return this;
    }

    @Override
    public Server shutdown() {
        isShutdownAndTerminated = true;
        return this;
    }

    @Override
    public Server shutdownNow() {
        isShutdownAndTerminated = true;
        return this;
    }

    @Override
    public boolean isShutdown() {
        return isShutdownAndTerminated;
    }

    @Override
    public boolean isTerminated() {
        return isShutdownAndTerminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public void awaitTermination() {
        // NO-OP as it's not necessary to await anything.
    }
}
