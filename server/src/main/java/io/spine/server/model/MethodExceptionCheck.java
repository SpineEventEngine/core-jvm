/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.model;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The checker of the exception types thrown by a {@link Method}.
 *
 * <p>This class checks whether exception types thrown by the method match the provided list of
 * allowed exception types.
 *
 * <p>If such check fails, the {@link IllegalStateException} will be thrown. If the check passes,
 * no action is performed.
 */
final class MethodExceptionCheck {

    private final Method method;
    private final @Nullable Class<? extends Throwable> allowedThrowable;

    /**
     * Creates new instance of the check for the passed method and allowed {@code Throwable}.
     */
    static MethodExceptionCheck check(Method method,
                                      @Nullable Class<? extends Throwable> allowedThrowable) {
        checkNotNull(method);
        return new MethodExceptionCheck(method, allowedThrowable);
    }

    private MethodExceptionCheck(Method method, @Nullable Class<? extends Throwable> throwable) {
        this.method = method;
        this.allowedThrowable = throwable;
    }

    /**
     * Obtain prohibited exception types thrown by the method.
     *
     * @return the list containing prohibited exceptions, or
     *         an empty list if the method does not declare such exceptions
     */
    ImmutableList<Class<? extends Throwable>> findProhibited() {
        var thrownExceptions = method.getExceptionTypes();
        if (thrownExceptions.length == 0) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<Class<? extends Throwable>> result = ImmutableList.builder();
        for (var exceptionType : thrownExceptions) {
            if (allowedThrowable == null || !allowedThrowable.isAssignableFrom(exceptionType)) {
                @SuppressWarnings("unchecked")  // As all exceptions extend `Throwable`.
                var asThrowableCls =
                        (Class<? extends Throwable>) exceptionType;
                result.add(asThrowableCls);
            }
        }
        return result.build();
    }
}
