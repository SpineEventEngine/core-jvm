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
import io.spine.server.tuple.Element.CValue;
import io.spine.server.tuple.Element.DValue;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Optional;

import static io.spine.server.tuple.Element.value;
import static java.util.Optional.ofNullable;

/**
 * A tuple with four elements.
 *
 * <p>The first element must be a non-default {@link Message}
 * and not {@link com.google.protobuf.Empty Empty}.
 *
 * <p>Other three can be {@code Message}, {@link java.util.Optional Optional} or
 * {@link Either}.
 *
 * @param <A>
 *         the type of the first element
 * @param <B>
 *         the type of the second element
 * @param <C>
 *         the type of the third element
 * @param <D>
 *         the type of the fourth element
 */
public final class Quartet<A extends Message, B, C, D>
        extends Tuple
        implements AValue<A>, BValue<B>, CValue<C>, DValue<D> {

    @Serial
    private static final long serialVersionUID = 0L;

    private Quartet(A a, B b, C c, D d) {
        super(a, b, c, d);
    }

    /**
     * Creates a quartet with all values present.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    Quartet<A, B, C, D> of(A a, B b, C c, D d) {
        checkAllNotNullOrEmpty(a, b, c, d);
        var result = new Quartet<>(a, b, c, d);
        return result;
    }

    /**
     * Creates a quartet with one optional value.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    Quartet<A, B, C, Optional<D>> withNullable(A a, B b, C c, @Nullable D d) {
        checkAllNotNullOrEmpty(a, b, c);
        checkNotEmpty(d);
        var result = new Quartet<>(a, b, c, ofNullable(d));
        return result;
    }

    /**
     * Creates a quartet with two optional values.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    Quartet<A, B, Optional<C>, Optional<D>> withNullable2(A a, B b, @Nullable C c, @Nullable D d) {
        checkAllNotNullOrEmpty(a, b);
        checkAllNotEmpty(c, d);
        var result = new Quartet<>(a, b, ofNullable(c), ofNullable(d));
        return result;
    }

    /**
     * Creates a quartet with three optional values.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    Quartet<A, Optional<B>, Optional<C>, Optional<D>>
    withNullable3(A a, @Nullable B b, @Nullable C c, @Nullable D d) {
        checkNotNullOrEmpty(a);
        checkAllNotEmpty(b, c, d);
        var result = new Quartet<>(a, ofNullable(b), ofNullable(c), ofNullable(d));
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

    @Override
    public C getC() {
        return value(this, IndexOf.C);
    }

    @Override
    public boolean hasC() {
        var value = getC();
        return isOptionalPresent(value);
    }

    @Override
    public D getD() {
        return value(this, IndexOf.D);
    }

    @Override
    public boolean hasD() {
        var value = getD();
        return isOptionalPresent(value);
    }

    @CanIgnoreReturnValue
    private static <M extends Message> M checkNotNullOrEmpty(M value) {
        return checkNotNullOrEmpty(Quartet.class, value);
    }

    @CanIgnoreReturnValue
    private static <M extends Message> @Nullable M checkNotEmpty(@Nullable M value) {
        return checkNotEmpty(Quartet.class, value);
    }

    @SafeVarargs
    private static <M extends Message> void checkAllNotNullOrEmpty(M... values) {
        checkAllNotNullOrEmpty(Quartet.class, values);
    }

    @SafeVarargs
    private static <M extends Message> void checkAllNotEmpty(M... values) {
        checkAllNotEmpty(Quartet.class, values);
    }
}
