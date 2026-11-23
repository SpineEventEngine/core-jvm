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

package io.spine.testing.server.model;

import io.spine.server.model.Model;

import java.lang.reflect.Method;
import java.util.Arrays;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Utilities for tests that deal with {@link Model}.
 */
public final class ModelTests {

    /** Prevents instantiation of this utility class. */
    private ModelTests() {
    }

    /**
     * Obtains a method declared in the given class with the given name.
     *
     * @throws IllegalStateException
     *         if the class does not have such a method.
     */
    public static Method getMethod(Class<?> cls, String methodName) {
        var methods = cls.getDeclaredMethods();

        var result = Arrays.stream(methods)
                .filter(method -> methodName.equals(method.getName()))
                .findFirst()
                .orElseThrow(
                        () -> newIllegalStateException(
                                "No method named `%s` found in class `%s`.",
                                methodName,
                                cls.getName()
                        )
                );

        return result;
    }
}
