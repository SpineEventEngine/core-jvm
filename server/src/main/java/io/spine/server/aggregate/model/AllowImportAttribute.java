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

package io.spine.server.aggregate.model;

import com.google.errorprone.annotations.Immutable;
import io.spine.server.aggregate.Apply;
import io.spine.server.model.Attribute;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A meta-attribute of an {@linkplain io.spine.server.aggregate.Apply event applier method},
 * telling whether the aggregate that declares the method allows importing events accepted
 * by this method.
 *
 * @see io.spine.server.aggregate.Apply#allowImport()
 */
@Immutable
public enum AllowImportAttribute implements Attribute<Boolean> {

    ALLOW(true),

    DO_NOT_ALLOW(false);

    private final boolean value;

    AllowImportAttribute(boolean value) {
        this.value = value;
    }

    @Override
    public String parameter() {
        return "allowImport";
    }

    @Override
    public Boolean value() {
        return value;
    }

    /**
     * Obtains the value of this attribute for a method.
     *
     * <p>The passed method must be an {@linkplain Apply event applier}.
     *
     * @param method
     *         the method to inspect
     * @return the value of {@code AllowImportAttribute} for the given {@code Method}
     * @throws IllegalArgumentException
     *         if the passed method is not properly annotated
     */
    public static AllowImportAttribute of(Method method) {
        checkNotNull(method);
        var annotation = method.getAnnotation(Apply.class);
        checkArgument(annotation != null,
                      "The method `%s` is not annotated with `@Apply`.",
                      method);
        var result = annotation.allowImport() ? ALLOW : DO_NOT_ALLOW;
        return result;
    }
}
