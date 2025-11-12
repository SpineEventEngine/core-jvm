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

package io.spine.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.core.UserId;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * The abstract base for client-side requests which allows to customize error handling.
 */
public abstract class ClientRequestBase {

    private final UserId user;
    private final Client client;

    @Nullable
    private ErrorHandler streamingErrorHandler = null;
    @Nullable
    private ServerErrorHandler serverErrorHandler = null;

    /**
     * Creates a new instance with the given user ID and the {@code client} instance.
     */
    ClientRequestBase(UserId user, Client client) {
        this.user = checkNotDefaultArg(user);
        this.client = checkNotNull(client);
    }

    /**
     * Creates a new instance which obtains properties from the parent request.
     */
    ClientRequestBase(ClientRequestBase parent) {
        this(parent.user, parent.client);
        this.streamingErrorHandler = parent.streamingErrorHandler();
        this.serverErrorHandler = parent.serverErrorHandler();
    }

    /**
     * Obtains the ID of the user of the request.
     */
    protected final UserId user() {
        return user;
    }

    /**
     * Obtains the client instance that will perform the request.
     */
    protected final Client client() {
        return client;
    }

    /**
     * Assigns a handler for errors occurred when delivering messages from the server.
     *
     * <p>If such an error occurs, no more results are expected from the server.
     */
    @CanIgnoreReturnValue
    @OverridingMethodsMustInvokeSuper
    public ClientRequestBase onStreamingError(ErrorHandler handler) {
        this.streamingErrorHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Assigns a handler for an error occurred on the server-side (such as validation error)
     * in response to posting a request.
     */
    @CanIgnoreReturnValue
    @OverridingMethodsMustInvokeSuper
    public ClientRequestBase onServerError(ServerErrorHandler handler) {
        this.serverErrorHandler = checkNotNull(handler);
        return this;
    }

    final @Nullable ErrorHandler streamingErrorHandler() {
        return streamingErrorHandler;
    }

    final @Nullable ServerErrorHandler serverErrorHandler() {
        return serverErrorHandler;
    }
}
