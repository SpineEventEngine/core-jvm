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

import io.spine.string.Diags;

import java.util.Collection;

import static java.lang.String.format;

/**
 * Indicates that more than one handling method for the same message class are present
 * in the declaring class.
 */
public final class DuplicateReceptorError extends ModelError {

    private static final long serialVersionUID = 0L;

    DuplicateReceptorError(Class<?> declaringClass,
                           DispatchKey key,
                           String firstMethodName,
                           String secondMethodName) {
        super(format("The `%s` class defines more than one method with parameters `%s`." +
                             " Methods encountered: `%s`, `%s`.",
                     declaringClass.getName(), key,
                     firstMethodName, secondMethodName));
    }

    @SuppressWarnings("unused") /* Reserved for future use in code generation when
        more than two duplicating receptors would be reported at once. */
    DuplicateReceptorError(Collection<? extends Receptor<?, ?, ?, ?>> receptors) {
        super(listReceptors(receptors));
    }

    private static String listReceptors(Collection<? extends Receptor<?, ?, ?, ?>> receptors) {
        var backticked = receptors.stream()
                .collect(Diags.toEnumerationBackticked());
        return format("Receptors %s are clashing.%n" +
                              "Only one of them should receive this message type.",
                      backticked);
    }
}
