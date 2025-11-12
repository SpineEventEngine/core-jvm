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

package io.spine.server.tuple;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.server.tuple.Element.AValue;
import io.spine.server.tuple.Element.BValue;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.tuple.Element.value;
import static java.util.Optional.ofNullable;

/**
 * A tuple with two elements.
 *
 * <p>The first element must be a non-default {@link Message}
 * and not {@link com.google.protobuf.Empty Empty}.
 *
 * <p>The second element can be {@code Message}, {@link java.util.Optional Optional} or
 * {@link Either}.
 *
 * @param <A>
 *         the type of the first element
 * @param <B>
 *         the type of the second element
 */
public final class Pair<A extends Message, B>
        extends Tuple
        implements AValue<A>, BValue<B> {

    @Serial
    private static final long serialVersionUID = 0L;

    private Pair(A a, B b) {
        super(a, b);
    }

    /**
     * Creates a new pair of values.
     */
    public static <A extends Message, B extends Message> Pair<A, B> of(A a, B b) {
        var safeA = checkNotNullOrEmpty(a);
        var safeB = checkNotNullOrEmpty(b);
        var result = new Pair<>(safeA, safeB);
        return result;
    }

    /**
     * Creates a pair with optionally present second value.
     *
     * @see #withOptional(Message, Optional)
     */
    public static <A extends Message, B extends Message>
    Pair<A, Optional<B>> withNullable(A a, @Nullable B b) {
        checkNotNullOrEmpty(a);
        checkNotEmpty(b);
        var result = new Pair<>(a, ofNullable(b));
        return result;
    }

    /**
     * Creates a pair with optionally present second value.
     *
     * @see #withNullable(Message, Message)
     * @apiNote This method treats a special case of construction using already available
     *         instance of {@code Optional}. This avoids unwrapping of {@code Optional} which would
     *         have been required for passing an optional value to
     *         {@link #withNullable(Message, Message)}.
     */
    public static <A extends Message, B extends Message>
    Pair<A, Optional<B>> withOptional(
            A a,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") /* see @apiNote */ Optional<B> b
    ) {
        checkNotNullOrEmpty(a);
        checkNotNull(b);
        var result = new Pair<>(a, b);
        return result;
    }

    /**
     * Creates a pair with the second element of a type descending from {@link Either}.
     */
    public static <A extends Message, B extends Either> Pair<A, B> withEither(A a, B b) {
        checkNotNullOrEmpty(a);
        checkNotNull(b);
        var result = new Pair<>(a, b);
        return result;
    }

    @Override
    public A getA() {
        return value(this, IndexOf.A);
    }

    /**
     * Tells whether the first element of the tuple is set.
     *
     * <p>Always returns {@code true}.
     */
    @Override
    public boolean hasA() {
        return true;
    }

    @Override
    public B getB() {
        return value(this, IndexOf.B);
    }

    @Override
    public boolean hasB() {
        var value = getB();
        return isOptionalPresent(value);
    }

    @CanIgnoreReturnValue
    private static <M extends Message> M checkNotNullOrEmpty(M value) {
        return checkNotNullOrEmpty(Pair.class, value);
    }

    @CanIgnoreReturnValue
    private static <M extends Message> @Nullable M checkNotEmpty(@Nullable M value) {
        return checkNotEmpty(Pair.class, value);
    }
}
