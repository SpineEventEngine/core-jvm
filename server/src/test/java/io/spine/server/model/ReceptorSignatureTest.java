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
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public abstract class ReceptorSignatureTest<S extends ReceptorSignature<?, ?>> {

    @SuppressWarnings("unused")   /* It is a method source. */
    protected abstract Stream<Method> validMethods();

    @SuppressWarnings( "unused")   /* It is a method source. */
    protected abstract Stream<Method> invalidMethods();

    protected abstract S signature();

    @MuteLogging /* Signature mismatch warnings are expected. */
    @DisplayName("create handlers from valid methods")
    @ParameterizedTest
    @MethodSource("validMethods")
    @SuppressWarnings("ReturnValueIgnored")
        // we ignore the result of `orElseGet()` because it throws `AssertionFailedError`.
    final void testValid(Method method) {
        wrap(method).orElseGet(Assertions::fail);
    }

    /**
     * Tests that the handlers are not created from the methods, which signatures do not satisfy
     * the requirements implied by the method annotation, such as {@literal @Assign}.
     *
     * @implNote The classification API of {@link MethodSignature} declares
     *         a {@link SignatureMismatchException} thrown. In most of the cases, the exception is
     *         thrown if the given method does not satisfy the requirements.
     *         However, {@link io.spine.server.command.Command Command}-ing methods are used in two
     *         different scenarios by handling either a {@code Command} or {@code Event} message.
     *         Their behavior is to return {@code Optional.empty()} if no match is found.
     *         All these cases are covered by the implementation of this test.
     */
    @DisplayName("not create handlers from invalid methods")
    @ParameterizedTest
    @MethodSource("invalidMethods")
    protected final void testInvalid(Method method) {
        try {
            var result = wrap(method);
            if (result.isPresent()) {
                fail(String.format(
                        "Handler method `%s` should have had an invalid signature.", method
                ));
            }
        } catch (SignatureMismatchException ignored) {
            assertThrows(SignatureMismatchException.class, () -> wrap(method));
        }
    }

    @SuppressWarnings("rawtypes") // save on generic params of `HandlerMethod`.
    private Optional<? extends Receptor> wrap(Method method) {
        Optional<? extends Receptor> result = signature().classify(method);
        return result;
    }

    /**
     * Returns all methods of the class which are annotated by the specified annotation.
     */
    protected static ImmutableSet<Method>
    methodsAnnotatedWith(Class<? extends Annotation> annotationCls, Class<?> declaringCls) {
        var result =
                Stream.of(declaringCls.getDeclaredMethods())
                      .filter(m -> m.getDeclaredAnnotation(annotationCls) != null)
                      .collect(toImmutableSet());
        return result;
    }
}
