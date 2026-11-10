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

import com.google.common.collect.ImmutableList;
import io.spine.server.BoundedContextBuilder;
import io.spine.test.client.ClientTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("`Client` should pass error handlers to `ClientRequest` for")
public class ClientErrorHandlersTest extends AbstractClientTest {

    @SuppressWarnings("UnnecessaryLambda") /* We need the reference for later checking.
     Using a method reference as advised by Error Prone is not practical for the purpose of
     these tests. */
    private final ErrorHandler errorHandler = (throwable) -> {};

    @SuppressWarnings("UnnecessaryLambda")
    private final ServerErrorHandler serverErrorHandler = (message, errorCase) -> {};

    private ClientRequest request;

    @Override
    protected List<BoundedContextBuilder> contexts() {
        return ImmutableList.of(ClientTestContext.users());
    }

    /**
     * Adds custom error handlers for the client instance to be used in this test suite.
     */
    @Override
    protected Client.Builder newClientBuilder(String serverName) {
        var builder = super.newClientBuilder(serverName);
        builder.onStreamingError(errorHandler)
               .onServerError(serverErrorHandler);
        return builder;
    }

    @BeforeEach
    void createRequest() {
        request = client().asGuest();
    }

    @Test
    @DisplayName("a streaming error handler")
    void streamingHandler() {
        assertThat(request.streamingErrorHandler())
                .isEqualTo(errorHandler);
    }

    @Test
    @DisplayName("a posting error handler")
    void postingHandler() {
        assertThat(request.serverErrorHandler())
                .isEqualTo(serverErrorHandler);
    }
}
