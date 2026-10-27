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
        try {
            assertThat(test.context())
                    .isNotNull();
        } finally {
            // Close the context, so that the next test can build a context with the same name.
            test.closeContext();
        }
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
