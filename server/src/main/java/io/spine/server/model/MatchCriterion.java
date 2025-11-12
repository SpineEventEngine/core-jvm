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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

import static io.spine.server.model.MethodExceptionCheck.check;
import static io.spine.server.model.SignatureMismatch.Severity.ERROR;
import static io.spine.server.model.SignatureMismatch.Severity.WARN;
import static io.spine.string.Diags.toEnumerationBackticked;
import static java.lang.String.format;

/**
 * The criteria of {@linkplain Method method} matching to a certain {@linkplain ReceptorSignature
 * set of requirements}, applied to the receptors.
 *
 * <p>Each criterion defines the {@linkplain #severity() severity} of its violation.
 * Depending on it, the callees may refuse to work with the tested methods.
 *
 * <p>Additionally, upon testing the criteria provide a number of {@linkplain SignatureMismatch
 * signature mismatches}, that may later be used for diagnostic purposes.
 */
@Internal
public enum MatchCriterion {

    /**
     * The criterion, which checks that the method return type is among the
     * {@linkplain ReceptorSignature#returnTypes() expected}.
     */
    RETURN_TYPE(ERROR,
                "The return type does not match the constraints set for `@%s`-annotated method.") {
        @Override
        Optional<SignatureMismatch> test(Method method, ReceptorSignature<?, ?> signature) {
            if (signature.returnTypeMatches(method)) {
                return Optional.empty();
            }
            return createMismatch(signature.annotation());
        }
    },
    /**
     * The criterion for the method parameters to conform the
     * {@linkplain ReceptorSignature#params() requirements}.
     *
     * @see AllowedParams#findMatching(Method)
     */
    PARAMETERS(ERROR, "Invalid parameter types."
             + " Please refer to the documentation of `@%s` for allowed types of parameters.") {
        @Override
        Optional<SignatureMismatch> test(Method method, ReceptorSignature<?, ?> signature) {
            var matching = signature.params().findMatching(method);
            if (matching.isPresent()) {
                return Optional.empty();
            }
            return createMismatch(signature.annotation());
        }
    },

    /**
     * The criterion, which ensures that the method access modifier is among the
     * {@linkplain ReceptorSignature#modifier() expected}.
     */
    ACCESS_MODIFIER(WARN, "The access modifier is `%s`."
               + " We recommend it to be one of: %s."
               + " Please refer to the documentation of `@%s` for details.") {
        @Override
        Optional<SignatureMismatch> test(Method method, ReceptorSignature<?, ?> signature) {
            var recommended = signature.modifier();
            var hasMatch = recommended.stream().anyMatch(m -> m.test(method));
            if (hasMatch) {
                return Optional.empty();
            }
            return createMismatch(method, signature, recommended);
        }

        private Optional<SignatureMismatch>
        createMismatch(Method method,
                       ReceptorSignature<?, ?> signature,
                       ImmutableSet<AccessModifier> recommended) {
            var annotationName = signature.annotation().getSimpleName();
            var currentModifier = AccessModifier.fromMethod(method);
            return SignatureMismatch.create(
                    this, currentModifier, recommended, annotationName);
        }
    },

    /**
     * The criterion checking that the tested method throws only
     * {@linkplain ReceptorSignature#allowedThrowable() allowed exceptions}.
     */
    PROHIBITED_EXCEPTION(ERROR, "%s") {

        @Override
        Optional<SignatureMismatch> test(Method method, ReceptorSignature<?, ?> signature) {
            @Nullable Class<? extends Throwable> allowed =
                    signature.allowedThrowable().orElse(null);
            var checker = check(method, allowed);
            var prohibited = checker.findProhibited();
            if (prohibited.isEmpty()) {
                return Optional.empty();
            }
            var errorMessage = new ProhibitedExceptionMessage(prohibited, allowed);
            return SignatureMismatch.create(this, errorMessage);
        }
    };

    private final SignatureMismatch.Severity severity;
    private final String format;

    /**
     * Creates an instance with the given severity and the template of the signature
     * mismatch message.
     */
    MatchCriterion(SignatureMismatch.Severity severity, String format) {
        this.severity = severity;
        this.format = format;
    }

    /**
     * Tests the method against the rules defined by the signature.
     *
     * @param method
     *         the method to test
     * @param signature
     *         the signature to use in testing
     * @return {@link Optional#empty() Optional.empty()} if there was no mismatch,
     *         or the {@code Optional<SignatureMismatch>} of the mismatch detected.
     */
    abstract Optional<SignatureMismatch> test(Method method, ReceptorSignature<?, ?> signature);

    final SignatureMismatch.Severity severity() {
        return severity;
    }

    final String formatMsg(Object... args) {
        var message = format(Locale.ROOT, format, args);
        return message;
    }

    /**
     * Creates a mismatch with the passed method and the name as parameters.
     */
    final Optional<SignatureMismatch> createMismatch(Class<? extends Annotation> annotation) {
        var annotationName = annotation.getSimpleName();
        return SignatureMismatch.create(this, annotationName);
    }

    /**
     * Helper class for {@link #PROHIBITED_EXCEPTION} for composing an error message.
     */
    private static final class ProhibitedExceptionMessage {

        private static final String METHOD_THROWS = "The method throws `%s`. ";

        private final ImmutableList<Class<? extends Throwable>> declared;
        private final @Nullable Class<? extends Throwable> allowed;

        ProhibitedExceptionMessage(ImmutableList<Class<? extends Throwable>> declared,
                                   @Nullable Class<? extends Throwable> allowed) {
            this.declared = declared;
            this.allowed = allowed;
        }

        @Override
        public String toString() {
            if (allowed == null) {
                return format(
                        METHOD_THROWS + "Throwing is not allowed for this kind of methods.",
                        enumerateThrown()
                );
            }
            return format(
                    METHOD_THROWS + "Only `%s` is allowed for this kind of methods.",
                    enumerateThrown(),
                    allowed.getName()
            );
        }

        /**
         * Prints {@code Iterable} to {@code String}, separating elements with comma.
         */
        private String enumerateThrown() {
            return declared.stream()
                    .collect(toEnumerationBackticked());
        }
    }
}

