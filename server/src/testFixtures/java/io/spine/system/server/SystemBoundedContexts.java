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

package io.spine.system.server;

import io.spine.server.BoundedContext;

import static com.google.common.truth.Truth.assertThat;

/**
 * A test utility for working with system {@link BoundedContext}s.
 */
public final class SystemBoundedContexts {

    /**
     * Prevents the utility class instantiation.
     */
    private SystemBoundedContexts() {
    }

    /**
     * Extracts the {@code System} bounded context from the given bounded context.
     */
    public static SystemContext systemOf(BoundedContext context) {
        var client = context.systemClient();
        assertThat(client).isInstanceOf(DefaultSystemClient.class);
        var defaultClient = (DefaultSystemClient) client;
        return defaultClient.target();
    }
}
