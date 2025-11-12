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

package io.spine.server.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import io.spine.base.CommandMessage;
import io.spine.server.event.model.SubscriberMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.server.model.TypeMatcher.exactly;

/**
 * Provides information about parameters of a method.
 */
@Immutable
public final class MethodParams {

    private final ImmutableList<Class<?>> params;
    private final @Nullable ArgumentFilter filter;

    /**
     * Obtains parameters of the subscriber method.
     *
     * <p>Parameters may include an argument filter.
     */
    public static MethodParams of(SubscriberMethod method) {
        checkNotNull(method);
        var filter = method.filter();
        var paramTypes = method.rawMethod()
                               .getParameterTypes();
        return new MethodParams(filter, paramTypes);
    }

    /**
     * Obtains parameters from the passed method.
     */
    public static MethodParams of(Method method) {
        checkNotNull(method);
        return new MethodParams(null, method.getParameterTypes());
    }

    /**
     * Creates the instance with a single parameter of the passed type.
     */
    static MethodParams ofType(Class<?> type) {
        checkNotNull(type);
        return new MethodParams(null, type);
    }

    private MethodParams(@Nullable ArgumentFilter filter, Class<?>... types) {
        this(filter, ImmutableList.copyOf(types));
    }

    private MethodParams(@Nullable ArgumentFilter filter, ImmutableList<Class<?>> params) {
        this.filter = filter;
        this.params = params;
    }

    /** Obtains the number of parameters passed to the method. */
    public int size() {
        return params.size();
    }

    /**
     * Obtains the simple names of the method parameters.
     */
    public ImmutableList<String> simpleNames() {
        return params.stream()
                .map(Class::getSimpleName)
                .collect(toImmutableList());
    }

    /**
     * Obtains the argument filter if it is specified for the first argument of
     * this instance of method parameters.
     */
    public Optional<ArgumentFilter> filter() {
        return Optional.ofNullable(filter);
    }

    /**
     * Obtains the type of the method parameter.
     *
     * @param index the zero-based index of the type.
     */
    public Class<?> type(int index) {
        var type = params.get(index);
        return type;
    }

    /**
     * Verifies if the method has only one parameter matching the passed criterion.
     */
    public boolean is(TypeMatcher type) {
        if (size() != 1) {
            return false;
        }
        var firstParam = type(0);
        return type.test(firstParam);
    }

    /**
     * Verifies if these parameters are exactly of the respective passed types.
     */
    public boolean are(Class<?>... types) {
        if (size() != types.length) {
            return false;
        }

        for (var i = 0; i < size(); i++) {
            var actual = type(i);
            var expected = types[i];
            if (!exactly(expected).test(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifies if these parameters satisfy the respective type matchers.
     */
    public boolean match(TypeMatcher... criteria) {
        return match(ImmutableList.copyOf(criteria));
    }

    /**
     * Verifies if these parameters satisfy the respective type matchers.
     */
    public boolean match(List<TypeMatcher> criteria) {
        if (size() != criteria.size()) {
            return false;
        }

        for (var i = 0; i < size(); i++) {
            var actual = type(i);
            var matcher = criteria.get(i);
            if (!matcher.test(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds out if the first method parameter is a {@linkplain CommandMessage}.
     *
     * @param method
     *         the method to inspect
     * @return {@code true} if the first parameter is a {@code Command} message,
     *         {@code false} otherwise
     */
    public static boolean firstIsCommand(Method method) {
        checkNotNull(method);
        var params = of(method);
        if (params.size() == 0) {
            return false;
        }
        var result = CommandMessage.class.isAssignableFrom(params.type(0));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var other = (MethodParams) o;
        return params.equals(other.params) &&
                Objects.equals(filter, other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params, filter);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection") // others also have the `filter` field.
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("params", params)
                          .add("filter", filter)
                          .toString();
    }
}
