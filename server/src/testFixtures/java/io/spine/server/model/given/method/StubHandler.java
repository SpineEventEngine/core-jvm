/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.server.model.given.method;

import com.google.protobuf.BoolValue;
import io.spine.core.EventContext;
import io.spine.test.model.ModProjectCreated;
import io.spine.test.model.ModProjectStarted;

import java.io.IOException;
import java.lang.reflect.Method;

public class StubHandler {

    private boolean onInvoked;
    private boolean handleInvoked;

    private static void throwCheckedException(
            @SuppressWarnings("unused") BoolValue message) throws Exception {
        throw new IOException("Throw new checked exception");
    }

    private static void throwRuntimeException(
            @SuppressWarnings("unused") BoolValue message) throws RuntimeException {
        throw new RuntimeException("Throw new runtime exception");
    }

    public static Method getTwoParameterMethod() {
        Method method;
        Class<?> clazz = StubHandler.class;
        try {
            method = clazz.getMethod("on", ModProjectCreated.class, EventContext.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return method;
    }

    public static Method getOneParameterMethod() {
        Method method;
        Class<?> clazz = StubHandler.class;
        try {
            method = clazz.getDeclaredMethod("handle", ModProjectStarted.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return method;
    }

    public static Method getMethodWithCheckedException() {
        Method method;
        Class<?> clazz = StubHandler.class;
        try {
            method = clazz.getDeclaredMethod("throwCheckedException", BoolValue.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return method;
    }

    public static Method getMethodWithRuntimeException() {
        Method method;
        Class<?> clazz = StubHandler.class;
        try {
            method = clazz.getDeclaredMethod("throwRuntimeException", BoolValue.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return method;
    }

    @SuppressWarnings("unused" /* to declare a method with two params. */)
    public void on(ModProjectCreated message, EventContext context) {
        onInvoked = true;
    }

    @SuppressWarnings("unused") // The method is used via reflection.
    private void handle(ModProjectStarted message) {
        handleInvoked = true;
    }

    public boolean wasOnInvoked() {
        return onInvoked;
    }

    public boolean wasHandleInvoked() {
        return handleInvoked;
    }
}
