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

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;

import java.lang.reflect.TypeVariable;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Tells if a passed class satisfies some particular criterion.
 */
@Internal
@Immutable
public interface TypeMatcher extends Predicate<Class<?>> {

    /**
     * Creates a type matcher which matches the type if it is a class (i.e., not an interface)
     * implementing the given interface.
     */
    static TypeMatcher classImplementing(Class<?> iface) {
        checkNotNull(iface);
        checkArgument(iface.isInterface());
        return input -> !input.isInterface() && iface.isAssignableFrom(input);
    }

    /**
     * Creates a type matcher which matches the type is equal to the passed type.
     */
    static TypeMatcher exactly(Class<?> type) {
        checkNotNull(type);
        return input -> input.equals(type);
    }

    /**
     * Tells whether the {@code actual} type matches the {@code expected}.
     *
     * <h1>Use Cases</h1>
     *
     * <h2>Legend</h2>
     *
     * <p>Examples below refer to the types for simplicity;
     * in fact, the respective {@code TypeToken}s for the types are passed into the method.
     *
     * <h2>Rules</h2>
     *
     * <ul>
     *      <li> The same types always match;
     *      e.g. {@code UserId.class} always matches {@code UserId.class}.
     *
     *      <li> Different types always don't match.
     *      e.g. {@code UserId.class} always does not match {@code Nothing.class};
     *
     *      <li> If {@code actual} is a subtype of {@code expected}, they match.
     *      e.g. {@code matches(EventMessage, Nothing)} returns {@code true}.
     *
     *      <li> The generic parameters are taken into account in a similar manner:
     *      same always match, a subtype matches a parent type, different types aren't matching;
     *      e.g.
     *          {@literal matches(Collection<EventMessage>, Set<ProjectEvent>)} is {@code true};
     *          {@literal matches(Collection<EventMessage>, Set<CommandMessage>)} is {@code false}.
     *
     *      <li> If both {@code expected} and {@code actual} types define generic parameters,
     *      and the parameters contain {@code Optional<T>}, the latter is "unpacked" for comparison.
     *      e.g.
     *          {@literal matches(
     *                  Iterable<EventMessage>,
     *                  Triplet<ProjectCreated, ProjectAssigned, Optional<ProjectStarted>
     *              )} is {@code true},
     *          {@literal matches(Optional<EventMessage>, Optional<AddTask>)} is {@code false}.
     *
     *      <li> Generic parameters with wildcard are <em>not</em> supported.
     * </ul>
     */
    @SuppressWarnings("PMD.SimplifyBooleanReturns") // Here we chose readability over compactness.
    static boolean matches(TypeToken<?> expected, TypeToken<?> actual) {
        checkNotNull(expected);
        checkNotNull(actual);
        if (expected.equals(actual)) {
            return true;
        }

        var rawExpected = expected.getRawType();
        var rawActual = actual.getRawType();
        if (!rawExpected.isAssignableFrom(rawActual)) {
            return false;
        }

        TypeVariable<? extends Class<?>>[] expectedTypeParams = rawExpected.getTypeParameters();
        var expectedParamCount = expectedTypeParams.length;
        if (expectedParamCount == 0) {
            return true;
        }

        TypeVariable<? extends Class<?>>[] actualTypeParams = rawActual.getTypeParameters();

        if (expectedParamCount == 1) {
            var expectedGenericType = expected.resolveType(expectedTypeParams[0]);
            return matchActualGenericsToOne(actual, expectedGenericType);
        } else if (expectedParamCount != actualTypeParams.length) {
            return false;
        } else {
            return matchOneToOne(expected, actual);
        }
    }

    /**
     * Resolves the generic parameter of the passed type into an actual value.
     *
     * <p>If the resolved parameter type is {@code Optional<T>}, resolves its parameter {@code T}
     * and returns the result. In case the {@code Optional} does not have the generic parameter,
     * returns the type of {@code Optional} itself.
     */
    static TypeToken<?> resolve(TypeToken<?> type, TypeVariable<? extends Class<?>> param) {
        checkNotNull(type);
        checkNotNull(param);
        var actualGenericType = type.resolveType(param);
        var rawType = actualGenericType.getRawType();
        TypeVariable<? extends Class<?>>[] parameters = rawType.getTypeParameters();
        if (Optional.class.isAssignableFrom(rawType) && parameters.length == 1) {
            actualGenericType = actualGenericType.resolveType(parameters[0]);
        }
        return actualGenericType;
    }

    /**
     * Tells whether the {@code type} is not the same nor a descendant as {@code expectedSuper}.
     */
    static boolean differs(TypeToken<?> type, TypeToken<?> expectedSuper) {
        checkNotNull(type);
        checkNotNull(expectedSuper);
        return !type.equals(expectedSuper) && !type.isSubtypeOf(expectedSuper);
    }

    static boolean matchOneToOne(TypeToken<?> expected, TypeToken<?> actual) {
        checkNotNull(expected);
        checkNotNull(actual);
        var expectedTypeParams = genericTypesOf(expected);
        var actualTypeParams = genericTypesOf(actual);
        for (var paramIndex = 0; paramIndex < expectedTypeParams.length; paramIndex++) {
            var expectedGeneric = resolve(expected, expectedTypeParams[paramIndex]);
            var actualGeneric = resolve(actual, actualTypeParams[paramIndex]);
            if (differs(actualGeneric, expectedGeneric)) {
                return false;
            }
        }
        return true;
    }

    static TypeVariable<? extends Class<?>>[] genericTypesOf(TypeToken<?> type) {
        checkNotNull(type);
        return type.getRawType()
                   .getTypeParameters();
    }

    static boolean
    matchActualGenericsToOne(TypeToken<?> whoseGenerics, TypeToken<?> expectedGenericType) {
        checkNotNull(whoseGenerics);
        checkNotNull(expectedGenericType);
        var actualTypeParams = genericTypesOf(whoseGenerics);
        for (var param : actualTypeParams) {
            var actualGenericType = resolve(whoseGenerics, param);
            if (differs(actualGenericType, expectedGenericType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a set of {@link Message} types that are declared by the given type.
     *
     * <p>The types returned are as narrow as possible.
     *
     * <p>E.g. {@code Pair<TaskCreated, TaskAssigned>} returns {@literal Class<TaskCreated>} and
     * {@code Class<TaskAssigned>}.
     */
    static ImmutableSet<Class<? extends Message>> messagesFitting(TypeToken<?> type) {
        checkNotNull(type);
        var rawType = type.getRawType();
        TypeVariable<? extends Class<?>>[] parameters = rawType.getTypeParameters();
        if (0 == parameters.length) {
            return type.isSubtypeOf(Message.class)
                   ? ImmutableSet.of(asMessageType(rawType))
                   : ImmutableSet.of();
        }

        ImmutableSet.Builder<Class<? extends Message>> builder = ImmutableSet.builder();
        for (var parameter : parameters) {
            var genericType = resolve(type, parameter);
            if (genericType.isSubtypeOf(Message.class)) {
                builder.add(asMessageType(genericType.getRawType()));
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")  // Previously checked {@code cls} is a {@code Message} subtype.
    static Class<? extends Message> asMessageType(Class<?> cls) {
        checkNotNull(cls);
        return (Class<? extends Message>) cls;
    }
}
