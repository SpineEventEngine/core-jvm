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

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Empty;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Field;
import io.spine.base.FieldPath;
import io.spine.base.SignalMessage;
import io.spine.core.Where;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.model.AbstractReceptor.firstParamType;
import static io.spine.string.Stringifiers.fromString;

/**
 * Allows to filter messages passed by a handler method by a value of the message field.
 */
@Immutable
public final class ArgumentFilter implements Predicate<SignalMessage> {

    private static final ArgumentFilter acceptingAll =
            new ArgumentFilter(FieldPath.getDefaultInstance(), Empty.getDefaultInstance());

    private final @Nullable Field field;
    @SuppressWarnings("Immutable") // Values are primitives.
    private final @Nullable Object expectedValue;

    private ArgumentFilter(FieldPath path, Object expectedValue) {
        if (path.getFieldNameCount() > 0) {
            this.field = Field.withPath(path);
            this.expectedValue = expectedValue;
        } else {
            this.field = null;
            this.expectedValue = null;
        }
    }

    /**
     * Creates a new filter which accepts only the passed value of the specified field.
     */
    public static ArgumentFilter acceptingOnly(FieldPath field, Object fieldValue) {
        checkNotNull(field);
        checkNotNull(fieldValue);
        return new ArgumentFilter(field, fieldValue);
    }

    /**
     * Creates a filter which accepts all messages.
     */
    public static ArgumentFilter acceptingAll() {
        return acceptingAll;
    }

    /**
     * Creates a new filter by the passed method.
     *
     * <p>If the method is not annotated for filtering, the returned instance
     * {@linkplain ArgumentFilter#acceptsAll() accepts all} arguments.
     */
    public static ArgumentFilter createFilter(Method method) {
        @Nullable Where where = filterAnnotationOf(method);
        if (where != null) {
            var fieldPath = where.field();
            var value = where.equals();
            return createFilter(method, fieldPath, value);
        } else {
            return acceptingAll();
        }
    }

    /**
     * Checks if the filter is defined on the given {@code method}.
     *
     * @return {@code true} if a filter is defined and {@code false} if the filter would accept
     *         all arguments
     */
    public static boolean presentOn(Method method) {
        @Nullable Where annotation = filterAnnotationOf(method);
        return annotation != null;
    }

    /**
     * Creates a filter for the method using string values found in the annotation for the method.
     */
    private static ArgumentFilter createFilter(Method method, String fieldPath, String value) {
        var paramType = firstParamType(method);
        var field = Field.parse(fieldPath);
        var fieldType = field.findType(paramType).orElseThrow(() -> new ModelError(
                "The message with the type `%s` does not have the field `%s`.",
                paramType.getName(), field
        ));
        var expectedValue = fromString(value, fieldType);
        return acceptingOnly(field.path(), expectedValue);
    }

    private static @Nullable Where filterAnnotationOf(Method method) {
        var firstParam = firstParameterOf(method);
        return firstParam.getAnnotation(Where.class);
    }

    private static Parameter firstParameterOf(Method method) {
        var parameters = method.getParameters();
        checkArgument(parameters.length >= 1,
                      "The method `%s.%s()` does not have parameters.",
                      method.getDeclaringClass().getName(), method.getName());
        return parameters[0];
    }

    @VisibleForTesting
    @Nullable Object expectedValue() {
        return expectedValue;
    }

    /**
     * Tells if the passed filter works on the same field as this one.
     */
    boolean sameField(ArgumentFilter another) {
        return Objects.equals(field, another.field);
    }

    /** Obtains the depth of the filtered field. */
    public int pathLength() {
        if (field == null) {
            return 0;
        }
        return field.path().getFieldNameCount();
    }

    /** Tells if this filter accepts all the events. */
    public boolean acceptsAll() {
        return field == null;
    }

    /**
     * Accepts the passed event message if this filter {@linkplain #acceptingAll() accepts all}
     * events, or if the field of the message matches the configured value.
     */
    @Override
    public boolean test(SignalMessage msg) {
        if (acceptsAll()) {
            return true;
        }
        var eventField = field.valueIn(msg);
        var result = eventField.equals(expectedValue);
        return result;
    }

    @Override
    public String toString() {
        var helper = MoreObjects.toStringHelper(this);
        if (acceptsAll()) {
            helper.add("acceptsAll", true);
        } else {
            helper.add("field", field)
                  .add("expectedValue", expectedValue);
        }
        return helper.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var filter = (ArgumentFilter) o;
        return Objects.equals(field, filter.field) &&
                Objects.equals(expectedValue, filter.expectedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, expectedValue);
    }
}
