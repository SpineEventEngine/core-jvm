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

package io.spine.testing.server.model;

import io.spine.server.model.Model;

import java.lang.reflect.Method;
import java.util.Arrays;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Utilities for tests that deal with {@link Model}.
 *
 * @implNote The full name of this class is used by {@link Model#dropAllModels()} via
 *         a string literal for security check.
 */
public final class ModelTests {

    /** Prevents instantiation of this utility class. */
    private ModelTests() {
    }

    /**
     * Clears all models.
     *
     * @implNote This method is the only way to drop models because {@link Model#dropAllModels()}
     * verifies the name of the class which calls the method.
     * It must be {@code ModelTests this class}.
     */
    public static void dropAllModels() {
        Model.dropAllModels();
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
