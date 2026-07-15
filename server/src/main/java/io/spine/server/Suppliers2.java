/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server;

import com.google.common.base.Suppliers;
import io.spine.annotation.Internal;

import java.util.function.Supplier;

/**
 * Utilities for working with {@link Supplier}s, complementing
 * {@link Suppliers com.google.common.base.Suppliers}.
 */
@Internal
public final class Suppliers2 {

    /** Prevents instantiation of this utility class. */
    private Suppliers2() {
    }

    /**
     * Returns a supplier that obtains the value from the given delegate on the first call,
     * and returns that same value on the calls that follow.
     *
     * <p>Prefer this method over {@link Suppliers#memoize(com.google.common.base.Supplier)}
     * when the supplied value is not {@code null} — which, in this
     * {@link org.jspecify.annotations.NullMarked @NullMarked} code, is the usual case.
     * Guava declares its method as {@code <T extends @Nullable Object>}, deliberately, so that
     * a {@code null} value can be memoized too. The cost is that the nullness of {@code T} is
     * no longer inferred from the assignment, and assigning the result to a
     * {@code Supplier<@NonNull T>} reads as a nullness mismatch. Declaring {@code <T>} here,
     * in a {@code @NullMarked} package, binds {@code T} to a non-null type and settles it.
     *
     * @param delegate
     *         the supplier of the value to memoize
     * @param <T>
     *         the type of the supplied value
     */
    @SuppressWarnings("NullableProblems")
    public static <T> Supplier<T> memoize(Supplier<T> delegate) {
        return Suppliers.memoize(delegate::get);
    }
}
