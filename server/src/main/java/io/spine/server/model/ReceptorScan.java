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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import io.spine.type.MessageClass;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Scans a class for receptors by the given {@link ReceptorSignature}.
 *
 * @param <R>
 *         the type of the receptors to find
 */
final class ReceptorScan<R extends Receptor<?, ?, ?>> {

    private final Class<?> declaringClass;
    private final ReceptorSignature<R, ?> signature;
    private final Multimap<DispatchKey, R> receptors;
    private final Map<DispatchKey, R> seenMethods;
    private final Map<MessageClass<?>, Receptor<?, ?, ?>> messageToHandler;

    /**
     * Finds receptors in the scanned class by the given signature.
     *
     * @param <R>
     *         the type of the receptors
     * @param declaringClass
     *         the scanned class
     * @param signature
     *         the receptor {@linkplain ReceptorSignature signature}
     * @return the map with receptors of the given type
     */
    static <R extends Receptor<?, ?, ?>> ImmutableSetMultimap<DispatchKey, R>
    findMethodsBy(Class<?> declaringClass, ReceptorSignature<R, ?> signature) {
        checkNotNull(declaringClass);
        checkNotNull(signature);
        var operation = new ReceptorScan<>(declaringClass, signature);
        var result = operation.perform();
        return result;
    }

    private ReceptorScan(Class<?> declaringClass, ReceptorSignature<R, ?> signature) {
        this.declaringClass = declaringClass;
        this.signature = signature;
        this.receptors = HashMultimap.create();
        this.seenMethods = new HashMap<>();
        this.messageToHandler = new HashMap<>();
    }

    /**
     * Performs the operation.
     *
     * <p>Multiple calls to this method may cause {@link DuplicateReceptorError}s.
     */
    private ImmutableSetMultimap<DispatchKey, R> perform() {
        var declaredMethods = declaringClass.getDeclaredMethods();
        for (var method : declaredMethods) {
            if (!method.isBridge() && !method.isSynthetic()) {
                scanMethod(method);
            }
        }
        return ImmutableSetMultimap.copyOf(receptors);
    }

    private void scanMethod(Method method) {
        signature.classify(method)
                 .ifPresent(this::remember);
    }

    private void remember(R receptor) {
        checkNotRemembered(receptor);
        checkForFilterClashes(receptor);
        receptors.put(receptor.key().withoutFilter(), receptor);
    }

    private void checkNotRemembered(R receptor) {
        var key = receptor.key();
        if (seenMethods.containsKey(key)) {
            var alreadyPresent = seenMethods.get(key)
                                            .rawMethod();
            var methodName = alreadyPresent.getName();
            var duplicateMethodName = receptor.rawMethod().getName();
            throw new DuplicateReceptorError(
                    declaringClass, key, methodName, duplicateMethodName
            );
        } else {
            seenMethods.put(key, receptor);
        }
    }

    private void checkForFilterClashes(Receptor<?, ?, ?> receptor) {
        var filter = receptor.filter();
        if (filter.acceptsAll()) {
            return;
        }
        var receptorClass = receptor.messageClass();

        // It is OK to keep only the last filtering handler in the map (and not all of them)
        // because filtered fields are required to be the same.
        var existingReceptor = messageToHandler.put(receptorClass, receptor);
        if (existingReceptor != null && !filter.sameField(existingReceptor.filter())) {
            // There is already a receptor for this message class.
            // Check that the field that is used as the condition for filtering is the same.
            // It's not allowed to have filtered receptors by various fields because it
            // makes the dispatching ambiguous: "Do we need to dispatch to this receptor
            // and that receptor too?"
            //
            // We allow multiple receptors for the same message type with filters by
            // different values. It allows to split logic into smaller methods instead of having
            // if-else chains (that branch by different values) inside a bigger receptor method.
            //
            throw new HandlerFieldFilterClashError(
                    declaringClass, receptor.rawMethod(), existingReceptor.rawMethod()
            );
        }
    }
}
