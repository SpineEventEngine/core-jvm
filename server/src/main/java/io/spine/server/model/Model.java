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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.code.java.ClassName;
import io.spine.core.BoundedContext;
import io.spine.core.BoundedContextName;
import io.spine.core.BoundedContextNames;
import io.spine.reflect.PackageInfo;
import io.spine.security.InvocationGuard;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static com.google.common.collect.Maps.newConcurrentMap;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;

/**
 * Stores information of message handling classes.
 */
@Internal
public class Model {

    /**
     * Maps a raw class of a model object to a corresponding Model instance.
     */
    private static final Map<Class<?>, Model> models = newConcurrentMap();

    /**
     * The cache of {@code BoundedContextName}s searched for particular raw classes.
     *
     * @see #findContext(Class)
     */
    private static final Cache<ClassName, Optional<BoundedContextName>> knownContexts =
            CacheBuilder.newBuilder().maximumSize(500).build();

    /** The name of the Bounded Context to which this instance belongs. */
    private final BoundedContextName context;

    /** Maps a raw Java class to a {@code ModelClass}. */
    private final ClassMap classes = new ClassMap();

    /**
     * Creates a new instance for the Bounded Context with the passed name. */
    private Model(BoundedContextName context) {
        this.context = context;
    }

    /**
     * Obtains the instance which belongs to the Bounded Context of the passed class.
     *
     * <p>The method tries to obtain the context from the annotations of the package "hierarchy"
     * to which the class belongs. If no annotation found,
     * {@linkplain BoundedContextNames#assumingTests() test-only} will be created.
     *
     * @see BoundedContext
     */
    public static synchronized <T> Model inContextOf(Class<? extends T> rawClass) {
        var model = models.get(rawClass);
        if (model != null) {
            return model;
        }
        var optional = findContext(rawClass);

        // If no name of a Bounded Context found, assume the default name.
        // This is a safety net for newcomers and our tests.
        // We may want to make this check strict and require specifying Bounded Context names.
        var context = optional.orElseGet(BoundedContextNames::assumingTests);

        // Try to find a Model if it already exists.
        var alreadyAvailable = models.values()
                .stream()
                .filter((m) -> m.context.equals(context))
                .findAny();
        if (alreadyAvailable.isPresent()) {
            return alreadyAvailable.get();
        }

        // Since a model is not found, create for new Bounded Context and associated with the
        // passed raw class.
        var newModel = new Model(context);
        models.put(rawClass, newModel);
        return newModel;
    }

    /**
     * Finds Bounded Context name in the package annotations of the raw class,
     * or in the packages into which the package of the class is nested.
     */
    @VisibleForTesting
    static <T> Optional<BoundedContextName> findContext(Class<? extends T> rawClass) {
        var className = ClassName.of(rawClass);
        try {
            return knownContexts.get(className, () -> {
                Optional<BoundedContextName> result;

                var pkg = PackageInfo.of(rawClass);
                var annotation = pkg.findAnnotation(BoundedContext.class);
                if (annotation.isEmpty()) {
                    result = Optional.empty();
                } else {
                    var nameAsString = annotation.get().value();
                    var contextName = BoundedContextNames.newName(nameAsString);
                    result = Optional.of(contextName);
                }
                return result;
            });
        } catch (ExecutionException e) {
            throw illegalStateWithCauseOf(e);
        }
    }

    /** Obtains the name of the bounded context which encapsulates this model. */
    public BoundedContextName contextName() {
        return context;
    }

    /**
     * Clears the classes already added to the {@code Model}.
     *
     * <p>This method can be useful when multiple Spine projects are processed under the same
     * static context, e.g., in tests.
     */
    private void clear() {
        classes.clear();
    }

    /**
     * Clears all models, and then clears the {@link #models} map.
     */
    private static void reset() {
        for (var model : models.values()) {
            model.clear();
        }
        models.clear();
    }

    /**
     * Clears all models and removes them.
     *
     * <p>This method must <em>not</em> be called from the production code.
     *
     * @throws SecurityException
     *         if called directly by a non-authorized class
     * @apiNote This method <em>may</em> be called indirectly from authorized tool classes
     *         or test utility classes.
     */
    @VisibleForTesting
    public static synchronized void dropAllModels() {
        InvocationGuard.allowOnly(
                "io.spine.server.model.ModelTest",
                "io.spine.testing.server.model.ModelTests",
                "io.spine.server.command.model.DuplicateHandlerCheck"
        );
        reset();
    }

    /**
     * Obtains the model class for the passed raw class.
     *
     * <p>If the model does not have the model class yet, it would be obtained
     * from the passed supplier and remembered.
     */
    <T, M extends ModelClass<T>>
    ModelClass<T> getClass(Class<? extends T> cls,
                           Class<M> classOfModelClass,
                           Supplier<ModelClass<T>> supplier) {
        var result = classes.get(cls, classOfModelClass, supplier);
        return result;
    }
}
