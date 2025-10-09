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

package io.spine.testing.server.blackbox;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.base.CommandMessage;
import io.spine.server.BoundedContextBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base for test suites based on {@link BlackBox}.
 *
 * <p>Such a test suite creates a new instance of {@link io.spine.server.BoundedContext
 * BoundedContext} using a builder which implementing classes must {@linkplain #contextBuilder()
 * provide}. The context under the test is created {@linkplain BeforeEach before each} test method
 * and closed {@linkplain AfterEach after each} test method automatically.
 *
 * <p>Testing methods {@linkplain #context() obtain the context} for:
 * <ol>
 *     <li>Sending signals under the test to the context via {@code receivesXxx()} methods, such as
 *         {@link BlackBox#receivesCommand(CommandMessage) receivesCommand()}.
 *     <li>Asserting results of handling the signals via {@code assertXxx()} methods,
 *     e.g. {@link BlackBox#assertEvents() assertEvents()}.
 * </ol>
 *
 * <p>Test suites derived from this class must NOT close the context directly. Doing so will fail
 * the test.
 */
public abstract class ContextAwareTest {

    private @Nullable BlackBox context;

    /**
     * Creates a new builder for the Bounded Context under the test.
     */
    protected abstract BoundedContextBuilder contextBuilder();

    @BeforeEach
    @OverridingMethodsMustInvokeSuper
    protected void createContext() {
        var contextBuilder = contextBuilder();
        requireNonNull(contextBuilder,
                       "`contextBuilder()` must return a non-null `BoundedContextBuilder`.");
        context = BlackBox.from(contextBuilder);
    }

    @AfterEach
    @OverridingMethodsMustInvokeSuper
    protected void closeContext() {
        context().close();
        context = null;
    }

    /**
     * Obtains test configuration and assertion API for the Bounded Context under the test.
     */
    protected BlackBox context() {
        return requireNonNull(
                context,
                "The `BoundedContext` under the test is already destroyed or not yet created."
        );
    }
}
