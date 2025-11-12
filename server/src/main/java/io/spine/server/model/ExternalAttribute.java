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

import com.google.errorprone.annotations.Immutable;
import io.spine.core.External;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A meta-attribute of a {@link Receptor}, telling whether it handles objects,
 * produced outside the current bounded context.
 *
 * @see External
 */
@Immutable
enum ExternalAttribute implements Attribute<Boolean> {

    /** An attribute value for the receptors, designed to handle external objects only. */
    EXTERNAL(true),

    /** An attribute value for the receptors, designed to handle domestic objects only. */
    DOMESTIC(false);

    private final boolean value;

    ExternalAttribute(boolean value) {
        this.value = value;
    }

    @Override
    public String parameter() {
        return External.class.getSimpleName();
    }

    @Override
    public Boolean value() {
        return value;
    }

    /**
     * Obtains the value of this attribute for a given method.
     *
     * @param method
     *         the method to inspect
     * @return the value of {@code ExternalAttribute} for the given {@code Method}
     */
    public static ExternalAttribute of(Method method) {
        checkNotNull(method);
        return isExternal(method) ? EXTERNAL : DOMESTIC;
    }

    private static boolean isExternal(Method method) {
        var params = method.getParameters();
        if (params.length == 0) {
            return false;
        }
        var firstParam = params[0];
        var hasAnnotation = firstParam.getAnnotation(External.class) != null;
        return hasAnnotation;
    }
}
