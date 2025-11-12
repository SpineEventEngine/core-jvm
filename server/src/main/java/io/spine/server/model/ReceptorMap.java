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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.annotations.Immutable;
import io.spine.logging.WithLogging;
import io.spine.server.type.EmptyClass;
import io.spine.server.type.EnvelopeWithOrigin;
import io.spine.server.type.SignalEnvelope;
import io.spine.type.MessageClass;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.server.model.ReceptorScan.findMethodsBy;
import static java.lang.String.format;
import static java.util.Comparator.comparing;

/**
 * Provides mapping from a class of messages to methods which handle such messages.
 *
 * @param <M>
 *         the type of messages
 * @param <P>
 *         the type of message classes produced by receptors
 * @param <R>
 *         the type of receptors
 */
@Immutable(containerOf = {"M", "R"})
public final class ReceptorMap<M extends MessageClass<?>,
                               P extends MessageClass<?>,
                               R extends Receptor<?, M, ?, P>>
        implements Serializable, WithLogging {

    @Serial
    private static final long serialVersionUID = 0L;

    private final ImmutableSetMultimap<DispatchKey, R> map;
    private final ImmutableSet<M> messageClasses;

    /**
     * Creates a map with receptors matching the given signature found in the given class.
     *
     * @param declaringClass
     *         the class to inspect
     * @param signature
     *         the signature of methods
     * @return new instance with methods of the given class matching the given signature
     */
    public static <M extends MessageClass<?>,
                   P extends MessageClass<?>,
                   R extends Receptor<?, M, ?, P>>
    ReceptorMap<M, P, R> create(Class<?> declaringClass, ReceptorSignature<R, ?> signature) {
        checkNotNull(declaringClass);
        checkNotNull(signature);
        var map = findMethodsBy(declaringClass, signature);
        var messageClasses = messageClassesFrom(map.values());
        return new ReceptorMap<>(map, messageClasses);
    }

    private ReceptorMap(ImmutableSetMultimap<DispatchKey, R> map, ImmutableSet<M> messageClasses) {
        this.map = map;
        this.messageClasses = messageClasses;
    }

    /**
     * Obtains classes of messages for which receptors are stored in this map.
     */
    public ImmutableSet<M> messageClasses() {
        return messageClasses;
    }

    /**
     * Returns {@code true} if the map contains a receptor that accepts the passed class
     * of messages, {@code false} otherwise.
     */
    public boolean containsClass(M messageClass) {
        return messageClasses.contains(messageClass);
    }

    /**
     * Obtains classes of messages which receptors satisfy the given {@code predicate}.
     *
     * @param predicate
     *         a predicate for receptors to filter the corresponding message classes
     */
    public ImmutableSet<M> messageClasses(Predicate<? super R> predicate) {
        Multimap<DispatchKey, R> filtered = Multimaps.filterValues(map, predicate::test);
        return messageClassesFrom(filtered.values());
    }

    /**
     * Obtains the classes of messages produced by the receptors in this map.
     */
    public ImmutableSet<P> producedTypes() {
        var result = map.values()
                .stream()
                .map(Receptor::producedMessages)
                .flatMap(Set::stream)
                .collect(toImmutableSet());
        return result;
    }

    /**
     * Obtains methods for handling messages of the given class and with the given origin.
     *
     * <p>If there is no receptor matching both the message and origin class, a receptor will be
     * searched by the message class only.
     *
     * <p>If no receptor for a specified criteria is found, returns an empty set.
     *
     * @param messageClass
     *         the class of the accepted messages
     * @param originClass
     *         the class of the message from which the accepted message originates
     */
    private ImmutableSet<R> receptorsOf(M messageClass, MessageClass<?> originClass) {
        var cls = messageClass.value();
        var key =
                originClass.equals(EmptyClass.instance())
                ? new DispatchKey(cls, null)
                : new DispatchKey(cls, originClass.value());
        // If we have a receptor with the origin type, use the key.
        // Otherwise, find receptors only by the first parameter.
        var presentKey = map.containsKey(key)
                         ? key
                         : new DispatchKey(cls, null);
        var receptors = map.get(presentKey);
        return receptors;
    }

    /**
     * Looks up a method for handling the given message.
     *
     * @param message
     *         a signal message which must be handled
     * @return a receptor for the given signal or {@code Optional.empty()}
     */
    public Optional<R> findReceptorFor(SignalEnvelope<?, ?, ?> message) {
        var messageClass = message.messageClass();
        MessageClass<?> originClass;
        if (message instanceof EnvelopeWithOrigin) {
            originClass = ((EnvelopeWithOrigin<?, ?, ?>) message).originClass();
        } else {
            originClass = EmptyClass.instance();
        }
        @SuppressWarnings("unchecked")
        var receptors = receptorsOf(((M) messageClass), originClass);
        return receptors.stream()
                       .sorted(comparing((R r) -> r.filter().pathLength()).reversed())
                       .filter(r -> r.filter().test(message.message()))
                       .findFirst();
    }

    /**
     * Obtains a method for handling the given message.
     *
     * @param message
     *         a signal message which must be handled
     * @return a receptor for the given signal
     * @throws ModelError
     *         if the receptor for the message was not found
     */
    @SuppressWarnings("FloggerLogString") // we use the formatted string two times.
    public R receptorFor(SignalEnvelope<?, ?, ?> message) {
        var receptor = findReceptorFor(message);
        return receptor.orElseThrow(() -> {
            var msg = format(
                    "No receptor method found for the type `%s`.", message.messageClass()
            );
            logger().atError().log(() -> msg);
            return new ModelError(msg);
        });
    }

    /**
     * Obtains methods for handling messages of the given class.
     *
     * @param messageClass
     *         the message class of the handled message
     * @return handlers method
     */
    public ImmutableSet<R> receptorsOf(M messageClass) {
        return receptorsOf(messageClass, EmptyClass.instance());
    }

    private static <M extends MessageClass<?>, R extends Receptor<?, M, ?, ?>>
    ImmutableSet<M> messageClassesFrom(Collection<R> receptors) {
        var result = receptors.stream()
                .map(Receptor::messageClass)
                .collect(toImmutableSet());
        return result;
    }

    /**
     * Obtains handler methods matching the passed criteria.
     */
    public ImmutableCollection<R> methods() {
        return map.values();
    }
}
