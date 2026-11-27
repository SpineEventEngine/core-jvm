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

package io.spine.server.event.model.given.reactor;

import com.google.protobuf.Any;
import io.spine.base.Identifier;
import io.spine.core.Version;
import io.spine.server.event.EventReactor;
import io.spine.testing.server.model.ModelTests;

import java.lang.reflect.Method;

/**
 * Abstract base for test environment classes that allows to obtain a method reference.
 *
 * <p>The derived classes must declare a method named {@link #REACTOR_METHOD_NAME "react()"}
 * with appropriate arguments.
 */
public class TestEventReactor implements EventReactor {

    private static final String REACTOR_METHOD_NAME = "react";

    private final Any producerId = Identifier.pack(getClass().getName());

    @Override
    public Any producerId() {
        return producerId;
    }

    @Override
    public Version version() {
        return Version.getDefaultInstance();
    }

    public Method getMethod() {
        return ModelTests.getMethod(getClass(), REACTOR_METHOD_NAME);
    }
}
