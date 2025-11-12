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

import java.io.Serial;

import static io.spine.server.tuple.Element.value;

/**
 * A tuple containing only one element.
 *
 * <p>Used when returning an {@code Iterable} with a single element from a receptor
 * method, for better readability over {@code Iterable<E>} or {@code List<E>}.
 *
 * @param <A> the type of the element
 */
public class Single<A extends Message> extends Tuple implements AValue<A> {

    @Serial
    private static final long serialVersionUID = 0L;

    protected Single(A a) {
        super(a);
    }

    /**
     * Creates a new {@code Single} instance containing the specified value.
     */
    public static <A extends Message> Single<A> of(A a) {
        checkNotNullOrEmpty(Single.class, a);
        return new Single<>(a);
    }

    /**
     * Returns the contained value.
     */
    @Override
    public A getA() {
        return value(this, IndexOf.A);
    }

    /**
     * Always returns {@code true}.
     */
    @Override
    public boolean hasA() {
        return true;
    }
}
