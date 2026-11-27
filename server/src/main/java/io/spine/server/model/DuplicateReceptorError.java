/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.model;

import io.spine.string.Diags;

import java.util.Collection;

import static java.lang.String.format;

/**
 * Indicates that more than one handling method for the same message class is present
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
    DuplicateReceptorError(Collection<? extends Receptor<?, ?, ?>> receptors) {
        super(listReceptors(receptors));
    }

    private static String listReceptors(Collection<? extends Receptor<?, ?, ?>> receptors) {
        var backticked = receptors.stream()
                .collect(Diags.toEnumerationBackticked());
        return format("Receptors %s are clashing.%n" +
                              "Only one of them should receive this message type.",
                      backticked);
    }
}
