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
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Message;
import io.spine.base.Mistake;
import io.spine.base.RejectionThrowable;
import io.spine.core.Signal;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.Success;
import io.spine.server.log.ReceptorLifecycle;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static io.spine.base.Errors.causeOf;
import static io.spine.base.Errors.fromThrowable;
import static io.spine.server.model.MethodResults.collectMessageClasses;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

/**
 * An abstract base for wrappers over methods handling messages.
 *
 * <p>Two message handlers are equivalent when they refer to the same method on the
 * same object (not class).
 *
 * @param <T>
 *         the type of the target object
 * @param <M>
 *         the type of the message handled by this method
 * @param <C>
 *         the type of the message class
 * @param <E>
 *         the type of message envelopes, in which the messages to handle are wrapped
 * @param <R>
 *         the type of the produced message classes
 */
@Immutable
public abstract
class AbstractReceptor<T,
                       M extends Message,
                       C extends MessageClass<M>,
                       E extends MessageEnvelope<?, ? extends Signal<?, ?, ?>, ?>,
                       R extends MessageClass<?>>
        implements Receptor<T, C, E, R> {

    /** The method to be called. */
    @SuppressWarnings("Immutable")
    private final Method method;

    /** The class of the first parameter. */
    private final Class<M> messageClass;

    /**
     * The set of the metadata attributes set via method annotations.
     *
     * @implNote Even though that {@code MethodAttribute} is parameterized with {@code Object},
     * which is mutable, the {@code @Immutable} annotation of {@code MethodAttribute} class
     * ensures that we don't have mutable types passed as generic parameters.
     *
     * <p>This field is initialized in {@link #discoverAttributes()} to allow derived classes to
     * {@linkplain #attributeSuppliers() customize} the set of supported method attributes.
     */
    @SuppressWarnings({"Immutable", "NonFinalFieldInImmutable"})
    private ImmutableSet<Attribute<?>> attributes;

    /**
     * The specification of parameters for this method.
     *
     * @implNote It serves to extract the argument values from the {@linkplain E envelope} used
     * as a source for the method call.
     */
    private final ParameterSpec<E> parameterSpec;

    /**
     * Contains classes of messages returned by the receptor.
     *
     * <p>Does <em>not</em> contain interfaces.
     */
    @SuppressWarnings("Immutable") // Memoizing supplier is effectively immutable.
    private final Supplier<ImmutableSet<R>> producedTypes;

    @SuppressWarnings("Immutable") // because this `Supplier` is effectively immutable.
    private final Supplier<ArgumentFilter> filter = memoize(this::createFilter);

    /**
     * Creates a new instance to wrap {@code method} on {@code target}.
     *
     * @param method
     *         subscriber method
     * @param parameterSpec
     *         the specification of method parameters
     */
    protected AbstractReceptor(Method method, ParameterSpec<E> parameterSpec) {
        this.method = checkNotNull(method);
        this.messageClass = firstParamType(method);
        this.attributes = discoverAttributes(method);
        this.parameterSpec = parameterSpec;
        this.producedTypes = memoize(() -> collectMessageClasses(method));
        method.setAccessible(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Initializes attributes by obtaining values via functions provided by
     * {@link #attributeSuppliers()}.
     *
     * @see #attributeSuppliers()
     */
    @Override
    public final void discoverAttributes() {
        ImmutableSet.Builder<Attribute<?>> builder = ImmutableSet.builder();
        for (var fn : attributeSuppliers()) {
            var attr = fn.apply(method);
            builder.add(attr);
        }
        attributes = builder.build();
    }

    /**
     * Creates the filter for messages handled by this method.
     *
     * <p>If this method cannot have {@code @Where} filters but still defines one,
     * a {@link ModelError} is thrown.
     */
    protected ArgumentFilter createFilter() {
        return ArgumentFilter.createFilter(method);
    }

    @Override
    public final ArgumentFilter filter() {
        return filter.get();
    }

    protected static Method checkNotFiltered(Method method, String label) {
        if (ArgumentFilter.presentOn(method)) {
            throw new ModelError(
                    "A %s method cannot declare argument filters but `%s` does.", label, method
            );
        }
        return method;
    }

    /**
     * Obtains a set of functions for getting {@linkplain Attribute method attributes}
     * by a {@linkplain Method raw method} value.
     *
     * <p>Default implementation returns a one-element set for obtaining {@link ExternalAttribute}.
     *
     * <p>Overriding classes must return a set which is a
     * {@linkplain com.google.common.collect.Sets#union(Set, Set) union} of the
     * set provided by this method and the one needed by the overriding class.
     */
    @OverridingMethodsMustInvokeSuper
    protected Set<Function<Method, Attribute<?>>> attributeSuppliers() {
        return ImmutableSet.of(ExternalAttribute::of);
    }

    protected Class<? extends M> rawMessageClass() {
        return messageClass;
    }

    /**
     * Returns the class of the first parameter of the passed receptor.
     *
     * <p>It is expected that the first parameter of the passed method is always of
     * a class implementing {@link Message}.
     *
     * @param receptor
     *         the method to take the first parameter type from
     *         the class of the first method parameter
     * @throws ClassCastException
     *         if the first parameter is not a class implementing {@link Message}
     */
    protected static <M extends Message> Class<M> firstParamType(Method receptor) {
        @SuppressWarnings("unchecked" /* See Javadoc for the `receptor` parameter. */)
        var result = (Class<M>) receptor.getParameterTypes()[0];
        return result;
    }

    @Override
    public final Method rawMethod() {
        return method;
    }

    private int modifiers() {
        return method.getModifiers();
    }

    /**
     * Obtains the specification of parameters for this method.
     */
    protected ParameterSpec<E> parameterSpec() {
        return parameterSpec;
    }

    @Override
    public final Set<R> producedMessages() {
        return producedTypes.get();
    }

    /**
     * Returns {@code true} if the method is declared {@code public},
     * {@code false} otherwise.
     */
    protected final boolean isPublic() {
        var result = Modifier.isPublic(modifiers());
        return result;
    }

    /**
     * Returns {@code true} if the method is declared {@code private},
     * {@code false} otherwise.
     */
    protected final boolean isPrivate() {
        var result = Modifier.isPrivate(modifiers());
        return result;
    }

    @Override
    public final Set<Attribute<?>> attributes() {
        return attributes;
    }

    private static ImmutableSet<Attribute<?>> discoverAttributes(Method method) {
        checkNotNull(method);
        var externalAttribute = ExternalAttribute.of(method);
        return ImmutableSet.of(externalAttribute);
    }

    /**
     * Feeds the given {@code envelope} to the given {@code target} and returns the outcome.
     *
     * <p>If the target method throws {@link Mistake}, dispatching terminates with
     * rethrowing it.
     *
     * <p>Other types of exceptions are converted to {@link io.spine.base.Error} and returned
     * {@link DispatchOutcome.Builder#setError inside} the {@link DispatchOutcome}.
     *
     * @implNote The result of this method is not validated, as its fields in fact consist
     *         of the parts, such as wrapped {@code Command}s and {@code Event}s that are validated
     *         upon their creation. Such an approach allows improving the overall performance of
     *         the signal propagation.
     */
    @SuppressWarnings({
            "ChainOfInstanceofChecks" /* We need to separate exceptions. */,
            "ThrowInsideCatchBlockWhichIgnoresCaughtException",
            /* Rethrowing `Mistake`. See Javadoc. */
    })
    @Override
    public DispatchOutcome invoke(T target, E envelope) {
        checkNotNull(target);
        checkNotNull(envelope);
        checkAttributesMatch(envelope);
        var signal = envelope.outerObject().messageId();
        var outcome = DispatchOutcome.newBuilder()
                .setPropagatedSignal(signal);
        @SuppressWarnings("unchecked") // The cast is protected by the receptor creation.
        var lifecycle = target instanceof ReceptorLifecycle
                        ? (ReceptorLifecycle<T>) target
                        : null;
        if (lifecycle != null) {
            lifecycle.beforeInvoke(this);
        }
        try {
            var success = doInvoke(target, envelope);
            outcome.setSuccess(success);
        } catch (IllegalOutcomeException e) {
            var error = fromThrowable(e);
            outcome.setError(error);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            checkNotNull(cause);
            if (cause instanceof Mistake) {
                throw (Mistake) cause;
            } else if (cause instanceof RejectionThrowable) {
                var success = asRejection(target, envelope, (RejectionThrowable) cause);
                outcome.setSuccess(success);
            } else {
                var error = causeOf(cause);
                outcome.setError(error);
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw illegalStateWithCauseOf(e);
        } finally {
            if (lifecycle != null) {
                lifecycle.afterInvoke(this);
            }
        }
        return outcome.build();
    }

    private Success doInvoke(T target, E envelope)
            throws IllegalAccessException, InvocationTargetException {
        var arguments = parameterSpec.extractArguments(envelope);
        var rawOutput = arguments.invokeMethod(method, target);
        return toSuccessfulOutcome(rawOutput, target, envelope);
    }

    private Success asRejection(T target, E envelope, RejectionThrowable cause) {
        var maybeSuccess = handleRejection(target, envelope, cause);
        return maybeSuccess.orElseThrow(this::cannotThrowRejections);
    }

    private RuntimeException cannotThrowRejections() {
        var errorMessage = format("`%s` may not throw rejections.", this);
        return new IllegalOutcomeException(errorMessage);
    }

    protected Optional<Success> handleRejection(T target, E origin, RejectionThrowable throwable) {
        return Optional.empty();
    }

    /**
     * A callback to check the passed envelope matches the annotation attributes of the method.
     *
     * <p>The default implementation does nothing.
     * Descending classes may override for checking the match.
     *
     * @throws IllegalArgumentException
     *         the default implementation does not throw ever. Descending classes would throw
     *         if the annotation arguments do not match the message of the passed envelope.
     * @param envelope the envelope with the massed to handle
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // Optional for descendants.
    protected void checkAttributesMatch(E envelope) throws IllegalArgumentException {
        // Do nothing by default.
    }

    /**
     * Returns a full name of the handler method.
     *
     * <p>The full name consists of a fully qualified class name of the target object and
     * the method name separated with a dot character.
     *
     * @return full name of the subscriber
     */
    public String getFullName() {
        var template = "%s.%s(%s)";
        var className = method.getDeclaringClass()
                              .getName();
        var methodName = method.getName();
        var parameterTypes =
                Stream.of(method.getParameterTypes())
                      .map(Class::getSimpleName)
                      .collect(joining(", "));
        var result = format(template, className, methodName, parameterTypes);
        return result;
    }

    @Override
    public MethodParams params() {
        return MethodParams.ofType(messageClass().value());
    }

    @Override
    public int hashCode() {
        var prime = 31;
        return (prime * method.hashCode());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AbstractReceptor)) {
            return false;
        }
        var other = (AbstractReceptor<?, ?, ?, ?, ?>) obj;
        return Objects.equals(this.method, other.method);
    }

    /**
     * Obtains the full name of the handler method.
     *
     * @see #getFullName()
     */
    @Override
    public String toString() {
        return getFullName();
    }
}
