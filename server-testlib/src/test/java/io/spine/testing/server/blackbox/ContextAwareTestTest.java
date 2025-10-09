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

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ContextAwareTest` should")
class ContextAwareTestTest {

    private final ContextAwareTest test = new DummyTest();

    @Test
    @DisplayName("create a new context before each test")
    void createContext() throws NoSuchMethodException {
        assertAnnotated("createContext", BeforeEach.class);

        // Assert that the context is not yet created.
        assertThrows(NullPointerException.class, test::context);

        test.createContext();
        assertThat(test.context())
                .isNotNull();
    }

    @Test
    @DisplayName("close and dispose context after each test")
    void closeContext() throws NoSuchMethodException {
        assertAnnotated("closeContext", AfterEach.class);

        // Create the context so that we have a valid instance to close.
        test.createContext();

        test.closeContext();
        assertThrows(NullPointerException.class, test::context);
    }

    private static <T extends Annotation>
    void assertAnnotated(String methodName, Class<T> annotationClass)
            throws NoSuchMethodException {
        var method = ContextAwareTest.class.getDeclaredMethod(methodName);
        Annotation annotation = method.getAnnotation(annotationClass);
        assertThat(annotation).isNotNull();
    }

    private static class DummyTest extends ContextAwareTest {

        @Override
        protected BoundedContextBuilder contextBuilder() {
            return BoundedContext.singleTenant(getClass().getSimpleName());
        }
    }
}
