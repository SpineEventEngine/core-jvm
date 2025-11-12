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

import com.google.common.collect.ImmutableSet;
import io.spine.logging.WithLogging;
import io.spine.server.type.MessageEnvelope;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.model.AccessModifier.KOTLIN_INTERNAL;
import static io.spine.server.model.AccessModifier.PACKAGE_PRIVATE;
import static io.spine.server.model.AccessModifier.PROTECTED_CONTRACT;
import static java.util.stream.Collectors.toList;

/**
 * Specification of a {@link Receptor} signature.
 *
 * <p>Sets the requirements to meet for the {@linkplain Method java.lang.reflect.Method}
 * in order to be qualified as a receptor for the type of messages specified by the type
 * of the envelopes {@code <E>}.
 *
 * <p>By extending this base class, descendants define the number of requirements:
 * <ul>
 *     <li>{@linkplain #ReceptorSignature(Class) the method annotation},
 *     <li>{@linkplain #params() the specification of method parameters},
 *     <li>{@linkplain #modifier() the set of allowed access modifiers},
 *     <li>{@linkplain #returnTypes() the set of valid return types},
 *     <li>{@linkplain #allowedThrowable() the set of allowed exceptions}, that the method
 *          declares to throw (empty by default),
 *     <li>whether an {@linkplain #mayReturnIgnored() ignored result},
 *         such as {@link io.spine.server.event.NoReaction NoReaction}, may be returned.
 * </ul>
 *
 * @param <R>
 *         the type of the receptors
 * @param <E>
 *         the type of envelope, which is used to invoke the receptors
 */
public abstract class ReceptorSignature<R extends Receptor<?, ?, E, ?>,
                                        E extends MessageEnvelope<?, ?, ?>>
        implements WithLogging {

    private final Class<? extends Annotation> annotation;

    /**
     * Creates an instance of signature, defining the required annotation to be present
     * in the methods that are matched against this signature.
     */
    protected ReceptorSignature(Class<? extends Annotation> annotation) {
        this.annotation = checkNotNull(annotation);
    }

    /**
     * Obtains the specification of receptor parameters to meet.
     */
    public abstract AllowedParams<E> params();

    /**
     * Obtains the set of recommended access modifiers for the method.
     *
     * <p>Override this method to change the allowed access modifiers.
     *
     * @return {@link AccessModifier#PACKAGE_PRIVATE},
     *         {@link AccessModifier#PROTECTED_CONTRACT}, and, additionally for Kotlin,
     *         {@link AccessModifier#KOTLIN_INTERNAL}
     */
    protected ImmutableSet<AccessModifier> modifier() {
        return ImmutableSet.of(PACKAGE_PRIVATE, KOTLIN_INTERNAL, PROTECTED_CONTRACT);
    }

    /**
     * Obtains the set of valid return types.
     */
    protected abstract ReturnTypes returnTypes();

    /**
     * Obtains the type of {@code Throwable} which a method can declare.
     *
     * <p>A receptor may declare more than one {@code Throwable}, but they must
     * extend the same type required by this type of signature.
     *
     * <p>Default implementation returns empty {@code Optional}, which means that normally
     * a receptor does not throw.
     */
    protected Optional<Class<? extends Throwable>> allowedThrowable() {
        return Optional.empty();
    }

    /**
     * Checks whether the passed {@code method} matches the constraints set by this instance.
     *
     * <p>{@link SignatureMismatch.Severity#WARN WARN}-level mismatches are silently ignored
     * by this method. To obtain detailed information callees should use {@link #match(Method)}.
     *
     * @param method
     *         the method to check
     * @return {@code true} if the given method is annotated using the matching signature
     *         <strong>AND</strong> there is no
     *         {@link SignatureMismatch.Severity#ERROR ERROR}-level mismatches;
     *         {@code false} if the method is not annotated, or its annotation does not match
     *         this signature
     * @throws SignatureMismatchException
     *         in case of any {@link SignatureMismatch.Severity#ERROR ERROR}-level mismatches
     * @implNote This method never returns {@code false} for methods that contain
     *         matching {@linkplain #annotation() annotation}, but do not match otherwise.
     *         It throws instead.
     */
    public final boolean matches(Method method) throws SignatureMismatchException {
        if (skipMethod(method)) {
            return false;
        }
        var mismatches = match(method);
        var hasErrors = mismatches.stream()
                .anyMatch(SignatureMismatch::isError);
        if (hasErrors) {
            throw new SignatureMismatchException(method, mismatches);
        }
        var warnings = mismatches.stream()
                .filter(SignatureMismatch::isWarning)
                .collect(toList());
        if (!warnings.isEmpty()) {
            var msg = SignatureMismatch.formatMsg(method, mismatches);
            logger().atWarning().log(() -> msg);
        }
        return true;
    }

    /**
     * Verifies if the passed return type conforms to this method signature.
     */
    final boolean returnTypeMatches(Method method) {
        var conforms = returnTypes().matches(method, mayReturnIgnored());
        return conforms;
    }

    /**
     * Determines, if the given raw {@code method} should be skipped as non-matching.
     *
     * <p>Such an approach allows improving the performance by skipping the methods that
     * a priori cannot be qualified as receptors matching this signature because they lack
     * the {@linkplain #annotation() required annotation}.
     *
     * @param method
     *         the method to determine if it should be inspected at all
     * @return {@code true} if this method should be walked through further examination,
     *         {@code false} otherwise
     */
    protected boolean skipMethod(Method method) {
        return !method.isAnnotationPresent(annotation);
    }

    /**
     * Creates a {@link Receptor} instance according to the passed
     * raw method and the parameter specification.
     *
     * <p>By implementing this method descendants define how the parameter spec is used to fit
     * the {@code Message} envelope onto the parameter list during the method invocation.
     *
     * <p>This method is designed to NOT perform any matching, but rather create a specific
     * instance of {@link Receptor}.
     *
     * @param method
     *         the raw method to wrap into a receptor instance being created
     * @param params
     *         the specification of method parameters
     * @return new receptor instance
     */
    public abstract R create(Method method, ParameterSpec<E> params);

    /**
     * Obtains the annotation, which is required to be declared for the matched raw method.
     */
    public final Class<? extends Annotation> annotation() {
        return annotation;
    }

    /**
     * Creates a {@linkplain Receptor receptor} from a raw method, if the passed
     * method {@linkplain #matches(Method) matches} the signature.
     *
     * @param method
     *         the method to convert to a receptor
     * @return the instance of receptor or empty {@code Optional} if the passed raw
     *         method does not {@linkplain #matches(Method) match}  the signature
     * @throws SignatureMismatchException
     *         in case there are {@link SignatureMismatch.Severity#ERROR ERROR}-level
     *         mismatches encountered
     */
    public final Optional<R> classify(Method method) throws SignatureMismatchException {
        var matches = matches(method);
        if (!matches) {
            return Optional.empty();
        }
        var matchingSpec = params().findMatching(method);
        return matchingSpec.map(spec -> {
            var receptor = create(method, spec);
            receptor.discoverAttributes();
            return receptor;
        });
    }

    /**
     * Match the method against the {@linkplain MatchCriterion criteria} and obtain a collection
     * of mismatches, if any.
     *
     * <p><strong>NOTE:</strong> this method does not test the presence of annotation.
     *
     * @param method
     *         the method to match.
     * @return the collection of signature mismatches, if any
     */
    public final Collection<SignatureMismatch> match(Method method) {
        Collection<SignatureMismatch> result =
                Stream.of(MatchCriterion.values())
                      .map(criterion -> criterion.test(method, this))
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .collect(toList());
        return result;
    }

    /**
     * Determines if a method with this signature may return an
     * {@linkplain MethodResult#isIgnored(Class) ignored} result.
     */
    public abstract boolean mayReturnIgnored();
}
