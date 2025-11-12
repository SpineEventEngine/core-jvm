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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Abstract base for tuple classes.
 */
public abstract class Tuple implements Iterable<Message>, Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    /**
     * Immutable list of tuple values.
     *
     * <p>Other entries can be either {@link Message} or {@link Optional}.
     */
    private final List<Element> values;

    /**
     * Creates a new instance with the passed values.
     */
    protected Tuple(Object... values) {
        super();

        ImmutableList.Builder<Element> builder = ImmutableList.builder();
        for (var value : values) {
            checkNotNull(value);
            var element = new Element(value);
            builder.add(element);
        }

        this.values = builder.build();
    }

    /**
     * Ensures that the passed message is not an instance of {@link Empty}.
     *
     * @return the passed value
     * @throws IllegalArgumentException if the passed value is {@link Empty}
     */
    @CanIgnoreReturnValue
    static <M extends Message, T extends Tuple>
    @Nullable M checkNotEmpty(Class<T> checkingClass, @Nullable M value) {
        if (value == null) {
            return null;
        }
        var isEmpty = value instanceof Empty;
        if (isEmpty) {
            var shortClassName = checkingClass.getSimpleName();
            throw newIllegalArgumentException(
                    "`%s` cannot have `Empty` elements. Use `Optional` instead",
                    shortClassName);
        }
        return value;
    }

    @CanIgnoreReturnValue
    @SuppressWarnings("ConstantConditions") /* `checkNotNull` is used by design. */
    static <M extends Message, T extends Tuple>
    M checkNotNullOrEmpty(Class<T> checkingClass, M value) {
        var result = checkNotEmpty(checkingClass, value);
        checkNotNull(result);
        return result;
    }

    @SafeVarargs
    static <M extends Message, T extends Tuple>
    void checkAllNotNullOrEmpty(Class<T> checkingClass, M... values) {
        for (var value : values) {
            checkNotNullOrEmpty(checkingClass, value);
        }
    }

    @SafeVarargs
    static <M extends Message, T extends Tuple>
    void checkAllNotEmpty(Class<T> checkingClass, M... values) {
        for (var value : values) {
            checkNotEmpty(checkingClass, value);
        }
    }

    @Override
    public final Iterator<Message> iterator() {
        Iterator<Message> result = new ExtractingIterator(values);
        return result;
    }

    /**
     * Obtains a value at the specified index.
     *
     * @param index a zero-based index value
     * @return the value at the index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    protected final Object get(int index) {
        var element = values.get(index);
        var result = element.value();
        return result;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public final boolean equals(Object obj) {
        return (this == obj) ||
                ((obj instanceof Tuple other) &&
                        Objects.equals(this.values, other.values));
    }

    /**
     * If the passed {@code value} is an {@code Optional}, tells if its value is present.
     *
     * <p>Otherwise, returns {@code true}.
     */
    static boolean isOptionalPresent(Object value) {
        checkNotNull(value);
        return !(value instanceof Optional<?> asOptional)
                || asOptional.isPresent();
    }

    /**
     * Traverses through elements obtaining a message value from them.
     */
    private static final class ExtractingIterator extends UnmodifiableIterator<Message> {

        private final Iterator<Element> source;

        private ExtractingIterator(Iterable<Element> source) {
            super();
            this.source = source.iterator();
        }

        @Override
        public boolean hasNext() {
            return source.hasNext();
        }

        @Override
        public Message next() {
            var next = source.next();
            var result = next.toMessage();
            return result;
        }
    }
}
