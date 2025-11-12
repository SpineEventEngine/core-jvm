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

import com.google.protobuf.Message;
import io.spine.server.tuple.Element.AValue;
import io.spine.server.tuple.Element.BValue;
import io.spine.server.tuple.Element.CValue;
import io.spine.server.tuple.Element.DValue;

import java.io.Serial;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A value which can be one of four possible types.
 *
 * @param <A> the type of the first alternative
 * @param <B> the type of the second alternative
 * @param <C> the type of the third alternative
 * @param <D> the type of the fourth alternative
 */
public final class EitherOf4<A extends Message,
                             B extends Message,
                             C extends Message,
                             D extends Message>
        extends Either
        implements AValue<A>, BValue<B>, CValue<C>, DValue<D> {

    @Serial
    private static final long serialVersionUID = 0L;

    private EitherOf4(Message value, IndexOf index) {
        super(value, index.value());
    }

    /**
     * Creates a new instance with {@code <A>} value.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    EitherOf4<A, B, C, D> withA(A a) {
        checkNotNull(a);
        var result = new EitherOf4<A, B, C, D>(a, IndexOf.A);
        return result;
    }

    /**
     * Creates a new instance with {@code <B>} value.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    EitherOf4<A, B, C, D> withB(B b) {
        checkNotNull(b);
        var result = new EitherOf4<A, B, C, D>(b, IndexOf.B);
        return result;
    }

    /**
     * Creates a new instance with {@code <C>} value.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    EitherOf4<A, B, C, D> withC(C c) {
        checkNotNull(c);
        var result = new EitherOf4<A, B, C, D>(c, IndexOf.C);
        return result;
    }

    /**
     * Creates a new instance with {@code <C>} value.
     */
    public static <A extends Message, B extends Message, C extends Message, D extends Message>
    EitherOf4<A, B, C, D> withD(D d) {
        checkNotNull(d);
        var result = new EitherOf4<A, B, C, D>(d, IndexOf.D);
        return result;
    }

    /**
     * Obtains the value of the first alternative.
     *
     * @throws IllegalStateException if a value of another type is stored instead.
     * @return the stored value
     * @see #hasA()
     */
    @Override
    public A getA() {
        return get(this, IndexOf.A);
    }

    /**
     * Tells whether {@code <A>} value is stored.
     *
     * @return {@code true} if the first alternative value is set, {@code false} otherwise
     */
    @Override
    public boolean hasA() {
        return IndexOf.A.is(index());
    }

    /**
     * Obtains the value of the second alternative.
     *
     * @throws IllegalStateException if a value of another type is stored instead.
     * @return the stored value
     * @see #hasB()
     */
    @Override
    public B getB() {
        return get(this, IndexOf.B);
    }

    /**
     * Tells whether {@code <B>} value is stored.
     *
     * @return {@code true} if the second alternative value is set, {@code false} otherwise
     */
    @Override
    public boolean hasB() {
        return IndexOf.B.is(index());
    }

    /**
     * Obtains the value of the third alternative.
     *
     * @throws IllegalStateException if a value of another type is stored instead.
     * @return the stored value
     * @see #hasC()
     */
    @Override
    public C getC() {
        return get(this, IndexOf.C);
    }

    /**
     * Tells whether {@code <C>} value is stored.
     *
     * @return {@code true} if the third alternative value is set, {@code false} otherwise
     */
    @Override
    public boolean hasC() {
        return IndexOf.C.is(index());
    }

    /**
     * Obtains the value of the third alternative.
     *
     * @throws IllegalStateException if a value of another type is stored instead.
     * @return the stored value
     * @see #hasD()
     */
    @Override
    public D getD() {
        return get(this, IndexOf.D);
    }

    /**
     * Tells whether {@code <D>} value is stored.
     *
     * @return {@code true} if the fourth alternative value is set, {@code false} otherwise
     */
    @Override
    public boolean hasD() {
        return IndexOf.D.is(index());
    }
}
