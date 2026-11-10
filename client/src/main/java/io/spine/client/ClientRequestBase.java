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

package io.spine.client;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.core.UserId;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * The abstract base for client-side requests that allows customizing error handling.
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
     * Creates a new instance that obtains properties from the parent request.
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
     * Assigns a handler for the error case that the server returns in response to posting
     * a request, such as {@code INVALID_REQUEST} for an invalid request.
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
