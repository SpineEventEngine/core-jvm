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

import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.server.event.NoReaction;
import io.spine.server.model.Nothing;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import static io.spine.util.Exceptions.newIllegalArgumentException;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * An element of a tuple.
 *
 * <p>Can hold either an {@link EventMessage}, or {@link Optional} message, or an instance of
 * {@link Either}.
 */
final class Element implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    private Object value;
    private Type type;

    /**
     * Creates a tuple element with a value which can be {@link Message},
     * {@link Optional}, or {@link Either}.
     */
    @SuppressWarnings({
            "ChainOfInstanceofChecks",
            "deprecation" /* Keep using `Nothing` for backward compatibility. */
    })
    Element(Object value) {
        if (value instanceof Either) {
            this.type = Type.EITHER;
        } else if (value instanceof Optional) {
            this.type = Type.OPTIONAL;
        } else if (value instanceof Message message) {
            // Treat `Nothing` (deprecated) and `NoReaction` as a special case,
            // allowing its default instance so that `Just<NoReaction>` is possible.
            var noReaction = value instanceof Nothing || value instanceof NoReaction;
            if (!noReaction) {
                checkNotDefault(message);
            }
            this.type = Type.MESSAGE;
        } else {
            throw newIllegalArgumentException(
                    "Tuple element of unsupported type passed: `%s`.", value
            );
        }

        this.value = value;
    }

    /**
     * Obtains the value of the element by its index and casts it to the type {@code <T>}.
     */
    @SuppressWarnings("TypeParameterUnusedInFormals") // See Javadoc.
    static <T> T value(Tuple tuple, IndexOf index) {
        @SuppressWarnings("unchecked") // The caller is responsible for the correct type.
        var value = (T) tuple.get(index.value());
        return value;
    }

    /**
     * Ensures that the passed message is not default or is an instance of {@link Empty}.
     */
    private static void checkNotDefault(Message value) {
        checkNotDefaultArg(
                value,
                "Tuples cannot contain default values. Default value of `%s` encountered.",
                value.getClass().getName()
        );
    }

    Object value() {
        return this.value;
    }

    Message toMessage() {
        switch (type) {
            case MESSAGE:
                return (Message) value;
            case EITHER:
                return ((Either) value).value();
            case OPTIONAL: {
                var optional = (Optional<?>) value;
                var result = optional.map(o -> (Message) o)
                                     .orElseGet(Empty::getDefaultInstance);
                return result;
            }
            default:
                throw uncoveredType();
        }
    }

    private IllegalStateException uncoveredType() {
        throw newIllegalStateException("Unsupported element type encountered: `%s`.", this.type);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(type);
        final Serializable obj;
        if (type != Type.OPTIONAL) {
            obj = (Serializable) value;
            out.writeObject(obj);
        } else /* (type == Type.OPTIONAL) */ {
            var optionalValue = (Optional<?>) value;
            obj = (Serializable) optionalValue.orElse(null);
        }
        out.writeObject(obj);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        type = (Type) in.readObject();
        if (type == Type.OPTIONAL) {
            value = Optional.ofNullable(in.readObject());
        }
        if (type != Type.OPTIONAL) {
            value = in.readObject();
        }
    }

    @SuppressWarnings("NonFinalFieldReferencedInHashCode")
        // The fields are non-final to support serialization.
        // Otherwise, they are set only in the constructor.
    @Override
    public int hashCode() {
        return Objects.hash(value, type);
    }

    @SuppressWarnings("NonFinalFieldReferenceInEquals")
        // The fields are non-final to support serialization.
        // Otherwise, they are set only in the constructor.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        var other = (Element) obj;
        return Objects.equals(this.value, other.value)
                && this.type == other.type;
    }

    private enum Type {
        MESSAGE,
        OPTIONAL,
        EITHER
    }

    interface AValue<T extends Message> {
        /**
         * Obtains the first element of the tuple.
         */
        T getA();

        /**
         * Tells whether the first element of the tuple is set.
         */
        boolean hasA();
    }

    /**
     * A marker interface for a tuple element which value can be
     * {@link java.util.Optional Optional}.
     */
    interface OptionalValue {
    }

    interface BValue<T> extends OptionalValue {
        /**
         * Obtains the second element of the tuple.
         */
        T getB();

        /**
         * Tells whether the second element of the tuple is set.
         */
        boolean hasB();
    }

    interface CValue<T> extends OptionalValue {
        /**
         * Obtains the third element of the tuple.
         */
        T getC();

        /**
         * Tells whether the third element of the tuple is set.
         */
        boolean hasC();
    }

    interface DValue<T> extends OptionalValue {
        /**
         * Obtains the fourth element of the tuple.
         */
        T getD();

        /**
         * Tells whether the fourth element of the tuple is set.
         */
        boolean hasD();
    }

    interface EValue<T> extends OptionalValue {
        /**
         * Obtains the fifth element of the tuple.
         */
        T getE();

        /**
         * Tells whether the fifth element of the tuple is set.
         */
        boolean hasE();
    }
}
