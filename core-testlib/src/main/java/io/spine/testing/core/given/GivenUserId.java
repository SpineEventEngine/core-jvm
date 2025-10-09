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

package io.spine.testing.core.given;

import io.spine.base.Identifier;
import io.spine.core.UserId;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory methods for creating test values of {@link io.spine.core.UserId UserId}.
 */
public final class GivenUserId {

    /**
     * The prefix for generated user identifiers.
     */
    private static final String USER_PREFIX = "user-";

    /** Prevent instantiation of this utility class. */
    private GivenUserId() {
    }

    /**
     * Creates a new user ID instance by passed string value.
     *
     * @param value new user ID value
     * @return new instance
     */
    public static UserId of(String value) {
        checkNotNull(value);
        checkArgument(!value.isEmpty(), "UserId cannot be empty");
        return UserId.newBuilder()
                     .setValue(value)
                     .build();
    }

    /**
     * Generates a new UUID-based {@code UserId}.
     */
    public static UserId newUuid() {
        return of(USER_PREFIX + Identifier.newUuid());
    }

    /**
     * Generates a new UUID-based {@code UserId}.
     *
     * @apiNote This method is an alias for {@link #newUuid()}. The reason for having it this.
     * The code {@code GivenUserId.newUuid()} is somewhat awkward to read and pronounce.
     * Some tests or test environments require setup where the code {@code GivenUserId.generated()}
     * reads natural as it tells a story. In other places having {@code newUuid()}
     * (which is statically imported) looks better, and it also clearly tells what it does.
     *
     * <p>So, this method is meant to be used with the class name, while its
     * {@linkplain #newUuid() sibling} is meant to be used when statically imported.
     */
    public static UserId generated() {
        return newUuid();
    }

    /**
     * Creates a test value of {@code UserId} based on the simple class name of a test suite.
     */
    public static UserId byTestClass(Class<?> cls) {
        checkNotNull(cls);
        return of(cls.getSimpleName());
    }
}
