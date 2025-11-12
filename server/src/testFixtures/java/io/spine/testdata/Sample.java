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

package io.spine.testdata;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.type.TypeUrl;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.Messages.builderFor;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;

/**
 * Utility for creating simple stubs for generated messages, DTOs (like {@link Event} and
 * {@link Command}), storage objects and more.
 */
public final class Sample {

    /**
     * The upper bound used for generating random {@code int} and {@code long} values.
     */
    private static final int INT_RANDOM_BOUND = 1654;

    /** Prevents instantiation of this utility class. */
    private Sample() {
    }

    /**
     * Generates a new stub {@link Message.Builder} with all the fields set to
     * {@link Random random} values.
     *
     * <p> All the fields are guaranteed to be not {@code null} and not default.
     * Number and {@code boolean} fields may or may not have their default values ({@code 0} and
     * {@code false}).
     *
     * @param clazz
     *         Java class of the stub message
     * @param <M>
     *         type of the required message
     * @param <B>
     *         type of the {@link Message.Builder} for the message
     * @return new instance of the {@link Message.Builder} for given type
     * @apiNote This method casts the builder to the generic parameter {@code <B>} for
     *         brevity of test code. It is the caller responsibility to ensure that the message
     *         type {@code <M>} corresponds to the builder type {@code <B>}.
     * @see #valueFor(FieldDescriptor)
     */
    @SuppressWarnings("TypeParameterUnusedInFormals") // See `apiNote`.
    public static <M extends Message, B extends Message.Builder> B builderForType(Class<M> clazz) {
        checkClass(clazz);
        @SuppressWarnings("unchecked") // We cast here for brevity of the test code.
        var builder = (B) builderFor(clazz);
        var builderDescriptor = builder.getDescriptorForType();
        Collection<FieldDescriptor> fields = builderDescriptor.getFields();
        for (var field : fields) {
            var value = valueFor(field);
            if (field.isRepeated()) {
                builder.addRepeatedField(field, value);
            } else {
                builder.setField(field, value);
            }
        }
        return builder;
    }

    /**
     * Generates a new stub {@link Message} with all the fields set to {@link Random random} values.
     *
     * <p> All the fields are guaranteed to be not {@code null} and not default.
     * Number and {@code boolean} fields
     * may or may not have their default values ({@code 0} and {@code false}).
     *
     * <p>If the required type is {@link Any}, an instance of an empty {@link Any} wrapped into
     * another {@link Any} is returned. See {@link AnyPacker}.
     *
     * @param clazz
     *         Java class of the required stub message
     * @param <M>
     *         type of the required message
     * @return new instance of the given {@link Message} type with random fields
     * @see #builderForType(Class)
     */
    public static <M extends Message> M messageOfType(Class<M> clazz) {
        checkClass(clazz);

        if (Any.class.equals(clazz)) {
            var any = Any.getDefaultInstance();
            @SuppressWarnings("unchecked") //
            var result = (M) AnyPacker.pack(any);
            return result;
        }

        var builder = builderForType(clazz);
        @SuppressWarnings("unchecked") // Checked cast
        var result = (M) builder.build();

        return result;
    }

    private static void checkClass(Class<? extends Message> clazz) {
        checkNotNull(clazz);
        // Support only generated protobuf messages
        checkArgument(Serializable.class.isAssignableFrom(clazz),
                      "Only generated protobuf messages are allowed.");
    }

    /**
     * Generates a non-default value for the given message field.
     *
     * <p>All the protobuf types are supported including nested {@link Message}s and
     * the {@code enum}s.
     *
     * <p>For {@code Integer}s and {@code Long}s only non-negative values are generated.
     * This is more convenient for using them as values of Spine internal messages,
     * such as {@code Version}, as only non-negative values may be accepted according
     * to their validation rules.
     *
     * @param field
     *         {@link FieldDescriptor} to take the type info from
     * @return a non-default generated value of type of the given field
     */
    @SuppressWarnings("BadImport" /* Use `Type` for brevity. */)
    private static Object valueFor(FieldDescriptor field) {
        var type = field.getType();
        var javaType = type.getJavaType();
        Random random = new SecureRandom();
        return switch (javaType) {
            case INT, LONG -> positiveInt(random);
            case FLOAT -> random.nextFloat();
            case DOUBLE -> random.nextDouble();
            case BOOLEAN -> random.nextBoolean();
            case STRING -> randomString();
            case BYTE_STRING -> {
                var randomString = randomString();
                yield ByteString.copyFrom(randomString, UTF_8);
            }
            case ENUM -> enumValueFor(field, random);
            case MESSAGE -> messageValueFor(field);
        };
    }

    private static String randomString() {
        return randomUUID().toString();
    }

    private static int positiveInt(Random random) {
        return random.nextInt(INT_RANDOM_BOUND) + 1;
    }

    private static Object enumValueFor(FieldDescriptor field, Random random) {
        var descriptor = field.getEnumType();
        var enumValues = descriptor.getValues();
        if (enumValues.isEmpty()) {
            throw newIllegalStateException(
                    "There must be at least one `Enum` value for field `%s`.", field
            );
        }

        // Value under index 0 is usually used to store `undefined` option
        // Use values with indexes from 1 to n
        var index = random.nextInt(enumValues.size() - 1) + 1;
        var enumValue = descriptor.findValueByNumber(index);
        return enumValue;
    }

    private static Message messageValueFor(FieldDescriptor field) {
        var messageType = TypeUrl.from(field.getMessageType());
        Class<? extends Message> javaClass = messageType.getMessageClass();
        var fieldValue = messageOfType(javaClass);
        return fieldValue;
    }
}
